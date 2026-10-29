package com.orbitai.assistant.voice

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.orbitai.assistant.util.Prefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Spoken replies.
 * 1) Built-in Android TTS with selectable voice (male preferred when "Adam-like").
 * 2) Optional ElevenLabs cloud voice "Adam" when user sets an ElevenLabs API key.
 *
 * Android does not ship a voice literally named "Adam"; that name is an ElevenLabs voice.
 */
class TtsHelper(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var mediaPlayer: MediaPlayer? = null
    var onSpeakingChanged: ((Boolean) -> Unit)? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            applyPreferredVoice()
            ready = true
        }
    }

    fun availableVoices(): List<Voice> {
        return try {
            tts?.voices?.toList()?.sortedBy { it.name } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun applyPreferredVoice() {
        val engine = tts ?: return
        val preferred = Prefs.ttsVoiceName
        val voices = try { engine.voices } catch (_: Exception) { null } ?: return

        val match = when {
            preferred.isNotBlank() ->
                voices.firstOrNull { it.name == preferred || it.name.contains(preferred, true) }
            Prefs.ttsUseAdamStyle ->
                // Prefer English male-ish voices on device
                voices.firstOrNull {
                    it.locale.language == "en" &&
                            (it.name.contains("male", true) ||
                                    it.name.contains("dsk", true) ||
                                    it.name.contains("iain", true) ||
                                    it.name.contains("daniel", true) ||
                                    it.name.contains("adam", true))
                } ?: voices.firstOrNull { it.locale.language == "en" }
            else -> voices.firstOrNull { it.locale.language == "en" }
        }
        if (match != null) {
            try { engine.voice = match } catch (_: Exception) {}
        }
        engine.setSpeechRate(Prefs.ttsSpeechRate)
        engine.setPitch(Prefs.ttsPitch)
    }

    fun speak(text: String) {
        if (!Prefs.ttsEnabled || text.isBlank()) return
        // Prefer ElevenLabs Adam when key is present and user wants Adam
        if (Prefs.ttsUseAdamStyle && Prefs.elevenLabsKey.isNotBlank()) {
            speakElevenLabsAdam(text)
            return
        }
        if (!ready) return
        applyPreferredVoice()
        onSpeakingChanged?.invoke(true)
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                onSpeakingChanged?.invoke(false)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onSpeakingChanged?.invoke(false)
            }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "orbit_${System.currentTimeMillis()}")
    }

    /**
     * ElevenLabs "Adam" voice id: pNInz6obpgDQGcFmaJgB (public catalog).
     * Requires user API key — never hard-coded as a paid bypass.
     */
    private fun speakElevenLabsAdam(text: String) {
        Thread {
            try {
                onSpeakingChanged?.invoke(true)
                val voiceId = Prefs.elevenLabsVoiceId.ifBlank { "pNInz6obpgDQGcFmaJgB" } // Adam
                val url = "https://api.elevenlabs.io/v1/text-to-speech/$voiceId"
                val body = JSONObject()
                    .put("text", text.take(2500))
                    .put("model_id", "eleven_monolingual_v1")
                    .put(
                        "voice_settings",
                        JSONObject().put("stability", 0.4).put("similarity_boost", 0.8)
                    )
                val req = Request.Builder()
                    .url(url)
                    .addHeader("xi-api-key", Prefs.elevenLabsKey)
                    .addHeader("Accept", "audio/mpeg")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        // Fallback to device TTS
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            if (ready) {
                                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "orbit_fb")
                            }
                            onSpeakingChanged?.invoke(false)
                        }
                        return@use
                    }
                    val bytes = resp.body?.bytes() ?: return@use
                    val file = File(context.cacheDir, "orbit_adam.mp3")
                    file.writeBytes(bytes)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        try {
                            mediaPlayer?.release()
                            mediaPlayer = MediaPlayer().apply {
                                setDataSource(file.absolutePath)
                                setOnCompletionListener {
                                    onSpeakingChanged?.invoke(false)
                                    it.release()
                                    mediaPlayer = null
                                }
                                setOnErrorListener { mp, _, _ ->
                                    onSpeakingChanged?.invoke(false)
                                    mp.release()
                                    true
                                }
                                prepare()
                                start()
                            }
                        } catch (_: Exception) {
                            onSpeakingChanged?.invoke(false)
                            if (ready) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "orbit_fb2")
                        }
                    }
                }
            } catch (_: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onSpeakingChanged?.invoke(false)
                    if (ready) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "orbit_fb3")
                }
            }
        }.start()
    }

    fun stop() {
        try { tts?.stop() } catch (_: Exception) {}
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        onSpeakingChanged?.invoke(false)
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
