package com.orbitai.assistant.ai.providers

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiProvider : AiProvider {
    override val id = ProviderId.GEMINI
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
            return ProviderReply(id.id, "", false, "No Gemini API key", 0)
        }
        val m = model.ifBlank { "gemini-2.5-flash" }
        val url = endpoint.ifBlank {
            "https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent?key=$apiKey"
        }
        return try {
            val contents = JSONArray()
            messages.takeLast(12).forEach { msg ->
                val role = if (msg.role == "assistant") "model" else "user"
                contents.put(
                    JSONObject().put("role", role)
                        .put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
                )
            }
            val body = JSONObject().put("contents", contents)
            if (systemPrompt.isNotBlank()) {
                body.put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                )
            }
            val req = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val ms = System.currentTimeMillis() - start
                if (!resp.isSuccessful) {
                    return ProviderReply(id.id, "", false, "HTTP ${resp.code}: ${raw.take(300)}", ms)
                }
                val text = JSONObject(raw)
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.trim()
                    .orEmpty()
                ProviderReply(id.id, text, text.isNotBlank(), if (text.isBlank()) "Empty" else null, ms)
            }
        } catch (e: Exception) {
            ProviderReply(id.id, "", false, e.message, System.currentTimeMillis() - start)
        }
    }
}
