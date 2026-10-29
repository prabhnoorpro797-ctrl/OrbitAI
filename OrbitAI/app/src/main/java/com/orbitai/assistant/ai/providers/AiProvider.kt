package com.orbitai.assistant.ai.providers

data class ChatMessage(val role: String, val content: String)

data class ProviderReply(
    val providerId: String,
    val text: String,
    val success: Boolean,
    val error: String? = null,
    val latencyMs: Long = 0
)

enum class ProviderId(val id: String, val displayName: String) {
    OPENAI("openai", "OpenAI"),
    GROQ("groq", "Groq"),
    XAI("xai", "xAI Grok"),
    ANTHROPIC("anthropic", "Anthropic Claude"),
    GEMINI("gemini", "Google Gemini"),
    OLLAMA("ollama", "Ollama (local)"),
    CUSTOM("custom", "Custom endpoint")
}

interface AiProvider {
    val id: ProviderId
    suspend fun chat(
        messages: List<ChatMessage>,
        systemPrompt: String,
        model: String,
        apiKey: String,
        endpoint: String
    ): ProviderReply
}
