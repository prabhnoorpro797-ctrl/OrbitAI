package com.orbitai.assistant.memory

import android.content.Context

class MemoryRepository(context: Context) {
    private val dao = OrbitDatabase.get(context).memoryDao()

    suspend fun remember(key: String, content: String, category: String = "general"): Long {
        val cleanKey = key.trim().take(80)
        val cleanContent = content.trim().take(2000)
        if (looksLikeSecret(cleanContent) || looksLikeSecret(cleanKey)) {
            throw IllegalArgumentException("Refusing to store secrets (API keys/passwords) as memory.")
        }
        return dao.insert(
            MemoryEntity(key = cleanKey, content = cleanContent, category = category)
        )
    }

    suspend fun search(query: String): List<MemoryEntity> = dao.search(query.trim())

    suspend fun all(): List<MemoryEntity> = dao.allMemories()

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clearMemories() = dao.clearAll()

    suspend fun addChat(role: String, content: String, provider: String? = null) {
        dao.insertMessage(ChatMessageEntity(role = role, content = content, provider = provider))
    }

    suspend fun recentChat(limit: Int = 20): List<ChatMessageEntity> =
        dao.recentMessages(limit).reversed()

    suspend fun clearChat() = dao.clearMessages()

    suspend fun stats(): Pair<Int, Int> = dao.memoryCount() to dao.messageCount()

    /** Build a short context block for the system prompt. */
    suspend fun contextForPrompt(userText: String): String {
        val hits = search(userText).take(5)
        if (hits.isEmpty()) {
            val all = all().take(8)
            if (all.isEmpty()) return ""
            return all.joinToString("\n") { "- ${it.key}: ${it.content}" }
        }
        return hits.joinToString("\n") { "- ${it.key}: ${it.content}" }
    }

    private fun looksLikeSecret(s: String): Boolean {
        val lower = s.lowercase()
        if (lower.contains("api key") || lower.contains("apikey") || lower.contains("password") ||
            lower.contains("secret") || lower.contains("token")
        ) {
            // only block if it also looks like a credential value
            if (s.contains("sk-") || s.contains("ghp_") || s.length > 40 && s.none { it.isWhitespace() }) {
                return true
            }
        }
        if (s.startsWith("sk-") || s.startsWith("xai-")) return true
        return false
    }
}
