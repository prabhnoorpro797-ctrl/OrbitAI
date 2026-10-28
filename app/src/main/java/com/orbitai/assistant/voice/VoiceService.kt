package com.orbitai.assistant.voice

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.orbitai.assistant.OrbitApp
import com.orbitai.assistant.R
import com.orbitai.assistant.ui.MainActivity
import com.orbitai.assistant.util.Prefs
import java.util.Locale

/**
 * Optional "Hey Orbit" wake-word helper.
 *
 * How it works (honest limits):
 * - Runs as a visible foreground service with a persistent notification
 *   ("Listening for Hey Orbit") so Android shows the mic is in use.
 * - Uses short SpeechRecognizer sessions in a loop (not a true offline
 *   always-on DSP wake engine like Google Assistant).
 * - Looks for phrases containing "hey orbit", "hi orbit", or "orbit".
 * - On match, opens MainActivity with EXTRA_WAKE so the app starts listening
 *   for your command.
 * - Uses more battery than button-only mode. Disable when not needed.
 * - Requires RECORD_AUDIO + notification permission + user toggle in Settings.
 * - Does NOT work reliably with screen off / Doze on all devices; Samsung may
 *   further restrict background mic use — keep the notification visible and
 *   exclude Orbit from aggressive battery optimization if needed.
 */
class VoiceService : Service() {

    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var cooldownUntil = 0L

    private val restartRunnable = Runnable {
        if (running) startListeningCycle()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopWakeMode()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        Prefs.wakeWordEnabled = true
        startForeground(NOTIFICATION_ID, buildNotification("Listening for “Hey Orbit”…"))
        running = true
        startListeningCycle()
        return START_STICKY
    }

    private fun startListeningCycle() {
        if (!running) return
        if (System.currentTimeMillis() < cooldownUntil) {
            handler.postDelayed(restartRunnable, 800)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("Speech recognition unavailable on this device")
            handler.postDelayed(restartRunnable, 5000)
            return
        }

        try {
            recognizer?.destroy()
        } catch (_: Exception) {}

        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    updateNotification("Listening for “Hey Orbit”…")
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    // No match / timeout are normal; restart after a short pause
                    val delay = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 600L
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1500L
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> 3000L
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            stopWakeMode()
                            stopSelf()
                            return
                        }
                        else -> 1200L
                    }
                    handler.postDelayed(restartRunnable, delay)
                }

                override fun onResults(results: Bundle?) {
                    val texts = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?: emptyList()
                    val joined = texts.joinToString(" ").lowercase(Locale.getDefault())
                    if (containsWakePhrase(joined)) {
                        onWakeDetected(joined)
                    } else {
                        handler.postDelayed(restartRunnable, 400)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val texts = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?: return
                    val joined = texts.joinToString(" ").lowercase(Locale.getDefault())
                    if (containsWakePhrase(joined)) {
                        onWakeDetected(joined)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Prefer shorter sessions for wake detection
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            handler.postDelayed(restartRunnable, 2000)
        }
    }

    private fun containsWakePhrase(text: String): Boolean {
        if (text.isBlank()) return false
        val t = text.lowercase(Locale.getDefault())
        return t.contains("hey orbit") ||
                t.contains("hi orbit") ||
                t.contains("ok orbit") ||
                t.contains("okay orbit") ||
                // Single "orbit" only if it is the whole short utterance
                t.trim() == "orbit" ||
                t.trim().startsWith("orbit ")
    }

    private fun onWakeDetected(heard: String) {
        // Cooldown so we don't re-trigger immediately
        cooldownUntil = System.currentTimeMillis() + 4000
        updateNotification("Wake word heard — opening Orbit")

        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_WAKE, true)
            putExtra(EXTRA_WAKE_TEXT, heard)
        }
        startActivity(launch)

        // Resume listening after cooldown
        handler.postDelayed(restartRunnable, 4500)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = Intent(this, VoiceService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, OrbitApp.CHANNEL_VOICE)
            .setContentTitle("ORBIT AI — Hey Orbit")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_orbit)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Stop listening", stopPi)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun stopWakeMode() {
        running = false
        Prefs.wakeWordEnabled = false
        handler.removeCallbacksAndMessages(null)
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
    }

    override fun onDestroy() {
        stopWakeMode()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.orbitai.assistant.STOP_WAKE"
        const val EXTRA_WAKE = "orbit_wake"
        const val EXTRA_WAKE_TEXT = "orbit_wake_text"
        private const val NOTIFICATION_ID = 41

        fun start(context: android.content.Context) {
            val i = Intent(context, VoiceService::class.java)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: android.content.Context) {
            val i = Intent(context, VoiceService::class.java).apply { action = ACTION_STOP }
            context.startService(i)
        }
    }
}
