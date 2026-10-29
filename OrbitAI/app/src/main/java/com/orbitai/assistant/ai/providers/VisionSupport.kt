package com.orbitai.assistant.ai.providers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object VisionSupport {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun bitmapToJpegBase64(bytes: ByteArray, maxSide: Int = 1280): String {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return ""
        val scale = maxOf(bmp.width, bmp.height).toFloat().let { if (it > maxSide) maxSide / it else 1f }
        val w = (bmp.width * scale).toInt().coerceAtLeast(1)
        val h = (bmp.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** OpenAI-compatible vision (GPT-4o, etc.) */
    fun openAiVision(
        endpoint: String,
        apiKey: String,
        model: String,
        prompt: String,
        jpegBase64: String
    ): ProviderReply {
        val start = System.currentTimeMillis()
        return try {
            val content = JSONArray()
                .put(JSONObject().put("type", "text").put("text", prompt))
                .put(
                    JSONObject().put("type", "image_url").put(
                        "image_url",
                        JSONObject().put("url", "data:image/jpeg;base64,$jpegBase64")
                    )
                )
            val messages = JSONArray().put(
                JSONObject().put("role", "user").put("content", content)
            )
            val body = JSONObject()
                .put("model", model.ifBlank { "gpt-4o-mini" })
                .put("messages", messages)
                .put("max_tokens", 800)
            val req = Request.Builder()
                .url(endpoint.ifBlank { "https://api.openai.com/v1/chat/completions" })
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val ms = System.currentTimeMillis() - start
                if (!resp.isSuccessful) {
                    return ProviderReply("vision", "", false, "HTTP ${resp.code}: ${raw.take(250)}", ms)
                }
                val text = JSONObject(raw)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
                    .orEmpty()
                ProviderReply("vision", text, text.isNotBlank(), null, ms)
            }
        } catch (e: Exception) {
            ProviderReply("vision", "", false, e.message, System.currentTimeMillis() - start)
        }
    }

    fun geminiVision(apiKey: String, model: String, prompt: String, jpegBase64: String): ProviderReply {
        val start = System.currentTimeMillis()
        val m = model.ifBlank { "gemini-1.5-flash" }
        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent?key=$apiKey"
        return try {
            val parts = JSONArray()
                .put(JSONObject().put("text", prompt))
                .put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject().put("mime_type", "image/jpeg").put("data", jpegBase64)
                    )
                )
            val body = JSONObject().put(
                "contents",
                JSONArray().put(JSONObject().put("parts", parts))
            )
            val req = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val ms = System.currentTimeMillis() - start
                if (!resp.isSuccessful) {
                    return ProviderReply("vision", "", false, "HTTP ${resp.code}: ${raw.take(250)}", ms)
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
                ProviderReply("vision", text, text.isNotBlank(), null, ms)
            }
        } catch (e: Exception) {
            ProviderReply("vision", "", false, e.message, System.currentTimeMillis() - start)
        }
    }
}
