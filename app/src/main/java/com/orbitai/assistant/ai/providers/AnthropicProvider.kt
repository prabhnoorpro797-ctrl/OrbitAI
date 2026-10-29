package com.orbitai.assistant.ai.providers

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AnthropicProvider : AiProvider {
    override val id = ProviderId.ANTHROPIC
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
        if (apiKey.isBlank()) {
            return ProviderReply(id.id, "", false, "No Anthropic API key", 0)
        }
        val url = endpoint.ifBlank { "https://api.anthropic.com/v1/messages" }
        return try {
            val arr = JSONArray()
            messages.takeLast(16).forEach { m ->
                val role = if (m.role == "assistant") "assistant" else "user"
                arr.put(JSONObject().put("role", role).put("content", m.content))
            }
            val body = JSONObject()
                .put("model", model.ifBlank { "claude-3-5-haiku-latest" })
                .put("max_tokens", 1024)
                .put("messages", arr)
            if (systemPrompt.isNotBlank()) body.put("system", systemPrompt)

            val req = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val ms = System.currentTimeMillis() - start
                if (!resp.isSuccessful) {
                    return ProviderReply(id.id, "", false, "HTTP ${resp.code}: ${raw.take(300)}", ms)
                }
                val content = JSONObject(raw).optJSONArray("content")
                val text = content?.optJSONObject(0)?.optString("text")?.trim().orEmpty()
                ProviderReply(id.id, text, text.isNotBlank(), if (text.isBlank()) "Empty" else null, ms)
            }
        } catch (e: Exception) {
            ProviderReply(id.id, "", false, e.message, System.currentTimeMillis() - start)
        }
    }
}
