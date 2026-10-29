package com.orbitai.assistant

import com.orbitai.assistant.ai.providers.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderIdTest {
    @Test
    fun allProvidersHaveIds() {
        assertTrue(ProviderId.entries.size >= 6)
        assertEquals("groq", ProviderId.GROQ.id)
        assertEquals("xai", ProviderId.XAI.id)
        assertEquals("anthropic", ProviderId.ANTHROPIC.id)
    }
}
