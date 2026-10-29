package com.orbitai.assistant.ai

import com.orbitai.assistant.ai.providers.AiProvider
import com.orbitai.assistant.ai.providers.AnthropicProvider
import com.orbitai.assistant.ai.providers.ChatMessage
import com.orbitai.assistant.ai.providers.GeminiProvider
import com.orbitai.assistant.ai.providers.OpenAiCompatibleProvider
import com.orbitai.assistant.ai.providers.ProviderId
import com.orbitai.assistant.ai.providers.ProviderReply
import com.orbitai.assistant.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Routes chat to the active provider and supports Expert Panel multi-provider calls.
 */
class AiOrchestrator {

    private val providers: Map<ProviderId, AiProvider> = mapOf(
        ProviderId.OPENAI to OpenAiCompatibleProvider(
            ProviderId.OPENAI, "https://api.openai.com/v1/chat/completions"
        ),
        ProviderId.GROQ to OpenAiCompatibleProvider(
            ProviderId.GROQ, "https://api.groq.com/openai/v1/chat/completions"
        ),
        ProviderId.XAI to OpenAiCompatibleProvider(
            ProviderId.XAI, "https://api.x.ai/v1/chat/completions"
        ),
        ProviderId.ANTHROPIC to AnthropicProvider(),
        ProviderId.GEMINI to GeminiProvider(),
        ProviderId.OLLAMA to OpenAiCompatibleProvider(
            ProviderId.OLLAMA, "http://127.0.0.1:11434/v1/chat/completions"
        ),
        ProviderId.CUSTOM to OpenAiCompatibleProvider(
            ProviderId.CUSTOM, "https://api.openai.com/v1/chat/completions"
        )
    )

    fun activeProviderId(): ProviderId {
        val raw = Prefs.aiProvider.lowercase().trim()
        return ProviderId.entries.firstOrNull { it.id == raw } ?: ProviderId.OPENAI
    }

    suspend fun chat(
        userMessage: String,
        history: List<Pair<String, String>>,
        systemPrompt: String
    ): ProviderReply = withContext(Dispatchers.IO) {
        val pid = activeProviderId()
        val provider = providers[pid] ?: providers[ProviderId.OPENAI]!!
        val key = Prefs.keyFor(pid)
        val endpoint = Prefs.endpointFor(pid)
        val model = Prefs.modelFor(pid)
        val messages = history.map { ChatMessage(it.first, it.second) } +
                ChatMessage("user", userMessage)
        provider.chat(messages, systemPrompt, model, key, endpoint)
    }

    /** Expert Panel: fan-out to selected providers in parallel. */
    suspend fun expertPanel(
        question: String,
        selected: List<ProviderId>,
        systemPrompt: String = "Answer clearly and concisely."
    ): List<ProviderReply> = coroutineScope {
        selected.map { pid ->
            async(Dispatchers.IO) {
                val provider = providers[pid] ?: return@async ProviderReply(
                    pid.id, "", false, "Unknown provider"
                )
                provider.chat(
                    listOf(ChatMessage("user", question)),
                    systemPrompt,
                    Prefs.modelFor(pid),
                    Prefs.keyFor(pid),
                    Prefs.endpointFor(pid)
                )
            }
        }.awaitAll()
    }

    suspend fun healthCheck(pid: ProviderId): ProviderReply = withContext(Dispatchers.IO) {
        val provider = providers[pid] ?: return@withContext ProviderReply(pid.id, "", false, "Unknown")
        provider.chat(
            listOf(ChatMessage("user", "Reply with exactly: OK")),
            "You are a health check. Reply only OK.",
            Prefs.modelFor(pid),
            Prefs.keyFor(pid),
            Prefs.endpointFor(pid)
        )
    }
}
