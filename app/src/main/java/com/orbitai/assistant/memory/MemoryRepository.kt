package com.orbitai.assistant.memory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class MemoryItem(
    val id: Long,
    val key: String,
    val content: String,
    val category: String = "general",
    val createdAt: Long = System.currentTimeMillis()
)

data class ChatItem(
    val role: String,
    val content: String,
    val provider: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Lightweight on-device memory (no Room / kapt) so CI builds stay simple.
 */
class MemoryRepository(context: Context) {
    private val dir = File(context.filesDir, "orbit_memory").also { it.mkdirs() }
    private val memFile = File(dir, "memories.json")
    private val chatFile = File(dir, "chat.json")

    suspend fun remember(key: String, content: String, category: String = "general"): Long {
        val cleanKey = key.trim().take(80)
        val cleanContent = content.trim().take(2000)
        if (looksLikeSecret(cleanContent) || looksLikeSecret(cleanKey)) {
            throw IllegalArgumentException("Refusing to store secrets (API keys/passwords) as memory.")
        }
        val list = loadMemories().toMutableList()
        val id = (list.maxOfOrNull { it.id } ?: 0L) + 1
        list.add(0, MemoryItem(id, cleanKey, cleanContent, category))
        saveMemories(list.take(200))
        return id
    }

    suspend fun search(query: String): List<MemoryItem> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return all()
        return loadMemories().filter {
            it.key.lowercase().contains(q) || it.content.lowercase().contains(q)
        }.take(20)
    }

    suspend fun all(): List<MemoryItem> = loadMemories()

    suspend fun delete(id: Long) {
        saveMemories(loadMemories().filter { it.id != id })
    }

    suspend fun clearMemories() {
        memFile.writeText("[]")
    }

    suspend fun addChat(role: String, content: String, provider: String? = null) {
        val list = loadChat().toMutableList()
        list.add(ChatItem(role, content, provider))
        saveChat(list.takeLast(100))
    }

    suspend fun recentChat(limit: Int = 20): List<ChatItem> =
        loadChat().takeLast(limit)

    suspend fun clearChat() {
        chatFile.writeText("[]")
    }

    suspend fun stats(): Pair<Int, Int> = loadMemories().size to loadChat().size

    suspend fun contextForPrompt(userText: String): String {
        val hits = search(userText).take(5)
        val src = hits.ifEmpty { all().take(8) }
        if (src.isEmpty()) return ""
        return src.joinToString("\n") { "- ${it.key}: ${it.content}" }
    }

    private fun loadMemories(): List<MemoryItem> {
        if (!memFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(memFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MemoryItem(
                    o.optLong("id"),
                    o.optString("key"),
                    o.optString("content"),
                    o.optString("category", "general"),
                    o.optLong("createdAt")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveMemories(list: List<MemoryItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("key", it.key)
                    .put("content", it.content)
                    .put("category", it.category)
                    .put("createdAt", it.createdAt)
            )
        }
        memFile.writeText(arr.toString())
    }

    private fun loadChat(): List<ChatItem> {
        if (!chatFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(chatFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChatItem(
                    o.optString("role"),
                    o.optString("content"),
                    o.optString("provider", null),
                    o.optLong("createdAt")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveChat(list: List<ChatItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("role", it.role)
                    .put("content", it.content)
                    .put("provider", it.provider)
                    .put("createdAt", it.createdAt)
            )
        }
        chatFile.writeText(arr.toString())
    }

    private fun looksLikeSecret(s: String): Boolean {
        val lower = s.lowercase()
        if (s.startsWith("sk-") || s.startsWith("xai-")) return true
        if ((lower.contains("api key") || lower.contains("password")) &&
            s.length > 40 && s.none { it.isWhitespace() }
        ) return true
        return false
    }
}
