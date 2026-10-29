package com.orbitai.assistant.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.orbitai.assistant.ai.providers.ProviderId

object Prefs {
    private const val PREFS_NAME = "orbit_prefs"
    private const val SECURE_PREFS = "orbit_secure_prefs"

    private lateinit var prefs: SharedPreferences
    private lateinit var securePrefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        securePrefs = EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Active provider
    var aiProvider: String
        get() = prefs.getString("ai_provider", "openai") ?: "openai"
        set(value) = prefs.edit().putString("ai_provider", value).apply()

    var aiEndpoint: String
        get() = prefs.getString("ai_endpoint", "https://api.openai.com/v1/chat/completions") ?: ""
        set(value) = prefs.edit().putString("ai_endpoint", value).apply()

    var aiModel: String
        get() = prefs.getString("ai_model", "gpt-4o-mini") ?: "gpt-4o-mini"
        set(value) = prefs.edit().putString("ai_model", value).apply()

    // Legacy single key (maps to active provider)
    var apiKey: String
        get() = keyFor(ProviderId.entries.firstOrNull { it.id == aiProvider } ?: ProviderId.OPENAI)
        set(value) {
            val pid = ProviderId.entries.firstOrNull { it.id == aiProvider } ?: ProviderId.OPENAI
            setKeyFor(pid, value)
        }

    fun keyFor(pid: ProviderId): String =
        securePrefs.getString("key_${pid.id}", "") ?: ""

    fun setKeyFor(pid: ProviderId, value: String) {
        securePrefs.edit().putString("key_${pid.id}", value).apply()
    }

    fun endpointFor(pid: ProviderId): String {
        val custom = prefs.getString("endpoint_${pid.id}", null)
        if (!custom.isNullOrBlank()) return custom
        return when (pid) {
            ProviderId.OPENAI -> "https://api.openai.com/v1/chat/completions"
            ProviderId.GROQ -> "https://api.groq.com/openai/v1/chat/completions"
            ProviderId.XAI -> "https://api.x.ai/v1/chat/completions"
            ProviderId.ANTHROPIC -> "https://api.anthropic.com/v1/messages"
            ProviderId.GEMINI -> "" // built in provider with key query
            ProviderId.OLLAMA -> "http://127.0.0.1:11434/v1/chat/completions"
            ProviderId.CUSTOM -> aiEndpoint
        }
    }

    fun setEndpointFor(pid: ProviderId, value: String) {
        prefs.edit().putString("endpoint_${pid.id}", value).apply()
    }

    fun modelFor(pid: ProviderId): String {
        val custom = prefs.getString("model_${pid.id}", null)
        if (!custom.isNullOrBlank()) return custom
        return when (pid) {
            ProviderId.OPENAI -> "gpt-4o-mini"
            ProviderId.GROQ -> "llama-3.1-8b-instant"
            ProviderId.XAI -> "grok-2-latest"
            ProviderId.ANTHROPIC -> "claude-3-5-haiku-latest"
            ProviderId.GEMINI -> "gemini-1.5-flash"
            ProviderId.OLLAMA -> "llama3.2"
            ProviderId.CUSTOM -> aiModel
        }
    }

    fun setModelFor(pid: ProviderId, value: String) {
        prefs.edit().putString("model_${pid.id}", value).apply()
    }

    var searchEngine: String
        get() = prefs.getString("search_engine", "https://www.google.com/search?q=") ?: ""
        set(value) = prefs.edit().putString("search_engine", value).apply()

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean("wake_word_enabled", false)
        set(value) = prefs.edit().putBoolean("wake_word_enabled", value).apply()

    var floatingBubbleEnabled: Boolean
        get() = prefs.getBoolean("floating_bubble", false)
        set(value) = prefs.edit().putBoolean("floating_bubble", value).apply()

    var ttsEnabled: Boolean
        get() = prefs.getBoolean("tts_enabled", true)
        set(value) = prefs.edit().putBoolean("tts_enabled", value).apply()

    var offlineModePreferred: Boolean
        get() = prefs.getBoolean("offline_preferred", false)
        set(value) = prefs.edit().putBoolean("offline_preferred", value).apply()

    var memoryEnabled: Boolean
        get() = prefs.getBoolean("memory_enabled", true)
        set(value) = prefs.edit().putBoolean("memory_enabled", value).apply()

    var haUrl: String
        get() = prefs.getString("ha_url", "") ?: ""
        set(value) = prefs.edit().putString("ha_url", value).apply()

    var haToken: String
        get() = securePrefs.getString("ha_token", "") ?: ""
        set(value) = securePrefs.edit().putString("ha_token", value).apply()

    /** Providers enabled for Expert Panel (comma-separated ids). */
    var expertProviders: String
        get() = prefs.getString("expert_providers", "openai,groq") ?: "openai,groq"
        set(value) = prefs.edit().putString("expert_providers", value).apply()

    var ttsVoiceName: String
        get() = prefs.getString("tts_voice_name", "") ?: ""
        set(value) = prefs.edit().putString("tts_voice_name", value).apply()

    var ttsUseAdamStyle: Boolean
        get() = prefs.getBoolean("tts_adam", true)
        set(value) = prefs.edit().putBoolean("tts_adam", value).apply()

    var ttsSpeechRate: Float
        get() = prefs.getFloat("tts_rate", 1.0f)
        set(value) = prefs.edit().putFloat("tts_rate", value).apply()

    var ttsPitch: Float
        get() = prefs.getFloat("tts_pitch", 1.0f)
        set(value) = prefs.edit().putFloat("tts_pitch", value).apply()

    var elevenLabsKey: String
        get() = securePrefs.getString("elevenlabs_key", "") ?: ""
        set(value) = securePrefs.edit().putString("elevenlabs_key", value).apply()

    /** Default Adam voice id on ElevenLabs public catalog */
    var elevenLabsVoiceId: String
        get() = prefs.getString("elevenlabs_voice", "pNInz6obpgDQGcFmaJgB") ?: "pNInz6obpgDQGcFmaJgB"
        set(value) = prefs.edit().putString("elevenlabs_voice", value).apply()

    fun clearApiKey() {
        ProviderId.entries.forEach { securePrefs.edit().remove("key_${it.id}").apply() }
    }
}
