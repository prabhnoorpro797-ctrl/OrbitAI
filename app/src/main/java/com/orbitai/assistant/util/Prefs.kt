package com.orbitai.assistant.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure preferences. API keys are stored in EncryptedSharedPreferences.
 * Never log or export API keys.
 */
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

    // --- Secure ---
    var apiKey: String
        get() = securePrefs.getString("api_key", "") ?: ""
        set(value) = securePrefs.edit().putString("api_key", value).apply()

    // --- Regular ---
    var aiProvider: String
        get() = prefs.getString("ai_provider", "openai") ?: "openai"
        set(value) = prefs.edit().putString("ai_provider", value).apply()

    var aiEndpoint: String
        get() = prefs.getString("ai_endpoint", "https://api.openai.com/v1/chat/completions") ?: ""
        set(value) = prefs.edit().putString("ai_endpoint", value).apply()

    var aiModel: String
        get() = prefs.getString("ai_model", "gpt-4o-mini") ?: "gpt-4o-mini"
        set(value) = prefs.edit().putString("ai_model", value).apply()

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

    // Home Assistant example config (user must fill)
    var haUrl: String
        get() = prefs.getString("ha_url", "") ?: ""
        set(value) = prefs.edit().putString("ha_url", value).apply()

    var haToken: String
        get() = securePrefs.getString("ha_token", "") ?: ""
        set(value) = securePrefs.edit().putString("ha_token", value).apply()

    fun clearApiKey() {
        securePrefs.edit().remove("api_key").apply()
    }
}
