package com.orbitai.assistant.ai.providers

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible chat completions (OpenAI, Groq, xAI, many local servers, custom).
 */
class OpenAiCompatibleProvider(
    override val id: ProviderId,
    private val defaultEndpoint: String
) : AiProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    override suspend fun chat(
        messages: List<ChatMessage>,
        systemPrompt: String,
        model: String,
        apiKey: String,
        endpoint: String
    ): ProviderReply {
        val start = System.currentTimeMillis()
        val url = endpoint.ifBlank { defaultEndpoint }
        if (apiKey.isBlank() && id != ProviderId.OLLAMA && id != ProviderId.CUSTOM) {
            return ProviderReply(id.id, "", false, "No API key configured for ${id.displayName}", 0)
        }
        return try {
            val arr = JSONArray()
            if (systemPrompt.isNotBlank()) {
                arr.put(JSONObject().put("role", "system").put("content", systemPrompt))
            }
            messages.takeLast(16).forEach { m ->
                arr.put(JSONObject().put("role", m.role).put("content", m.content))
            }
            val body = JSONObject()
                .put("model", model.ifBlank { defaultModel() })
                .put("messages", arr)
                .put("temperature", 0.7)
                .put("max_tokens", 1024)

            val reqBuilder = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
            if (apiKey.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
            client.newCall(reqBuilder.build()).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val ms = System.currentTimeMillis() - start
                if (!resp.isSuccessful) {
                    return ProviderReply(id.id, "", false, "HTTP ${resp.code}: ${raw.take(300)}", ms)
                }
                val json = JSONObject(raw)
                val text = json.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
                    .orEmpty()
                if (text.isBlank()) {
                    ProviderReply(id.id, "", false, "Empty response", ms)
                } else {
                    ProviderReply(id.id, text, true, null, ms)
                }
            }
        } catch (e: Exception) {
            ProviderReply(id.id, "", false, e.message ?: "network error", System.currentTimeMillis() - start)
        }
    }

    private fun defaultModel(): String = when (id) {
        ProviderId.GROQ -> "llama-3.1-8b-instant"
        ProviderId.XAI -> "grok-4.7"
        ProviderId.OLLAMA -> "llama3.2"
        else -> "gpt-4o-mini"
    }
}
