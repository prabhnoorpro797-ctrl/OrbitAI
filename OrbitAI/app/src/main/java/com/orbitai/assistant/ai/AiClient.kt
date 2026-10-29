package com.orbitai.assistant.ai

import com.orbitai.assistant.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Configurable online AI client.
 * Supports OpenAI-compatible chat completions endpoints.
 * API key is NEVER hard-coded.
 */
class AiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class AiResponse(
        val text: String,
        val success: Boolean,
        val error: String? = null
    )

    suspend fun chat(
        userMessage: String,
        history: List<Pair<String, String>> = emptyList(),
        systemPrompt: String = DEFAULT_SYSTEM
    ): AiResponse = withContext(Dispatchers.IO) {
        val apiKey = Prefs.apiKey
        if (apiKey.isBlank()) {
            return@withContext AiResponse(
                text = "",
                success = false,
                error = "No API key configured. Open Settings → AI Provider and enter your key."
            )
        }

        val endpoint = Prefs.aiEndpoint.ifBlank {
            "https://api.openai.com/v1/chat/completions"
        }
        val model = Prefs.aiModel.ifBlank { "gpt-4o-mini" }

        try {
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
            history.takeLast(10).forEach { (role, content) ->
                messages.put(JSONObject().put("role", role).put("content", content))
            }
            messages.put(JSONObject().put("role", "user").put("content", userMessage))

            val bodyJson = JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("temperature", 0.7)
                .put("max_tokens", 1024)

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext AiResponse(
                        text = "",
                        success = false,
                        error = "AI service error ${response.code}: ${body.take(200)}"
                    )
                }
                val json = JSONObject(body)
                val content = json
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?: ""
                AiResponse(text = content.trim(), success = true)
            }
        } catch (e: Exception) {
            AiResponse(
                text = "",
                success = false,
                error = "Network or AI error: ${e.message ?: "unknown"}"
            )
        }
    }

    companion object {
        val DEFAULT_SYSTEM = """
            You are ORBIT AI, a helpful, concise voice assistant inspired by JARVIS.
            You run on an Android phone. Keep replies short and actionable.
            When the user asks to control the phone, reply with a clear intent in the form:
            ACTION: <type> | <payload>
            Supported types: OPEN_APP, OPEN_SETTINGS, OPEN_URL, SEARCH, CALL, MESSAGE, FLASHLIGHT, ROUTINE, SMART_HOME, UNKNOWN.
            For general conversation answer normally without the ACTION prefix.
        """.trimIndent()
    }
}
