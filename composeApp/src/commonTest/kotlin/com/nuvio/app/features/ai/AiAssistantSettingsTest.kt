package com.nuvio.app.features.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiAssistantSettingsTest {
    @Test
    fun providerChainStartsWithTheSelectedProviderAndKeepsOnlyConfiguredOnes() {
        val settings = AiAssistantSettings(
            enabled = true,
            provider = AiProvider.GEMINI,
            cerebrasApiKey = "c",
            geminiApiKey = "g",
            openRouterApiKey = "o",
        )

        assertEquals(listOf(AiProvider.GEMINI, AiProvider.CEREBRAS, AiProvider.OPENROUTER), settings.providerChain)
    }

    @Test
    fun selectedProviderWithoutKeyFallsBackToTheConfiguredOnes() {
        val settings = AiAssistantSettings(enabled = true, provider = AiProvider.CEREBRAS, groqApiKey = "g")

        assertEquals(listOf(AiProvider.GROQ), settings.providerChain)
        assertTrue(settings.isReady)
    }

    @Test
    fun assistantIsNotReadyWhenDisabledOrWithoutKeys() {
        assertFalse(AiAssistantSettings(enabled = true).isReady)
        assertFalse(AiAssistantSettings(enabled = false, cerebrasApiKey = "c").isReady)
    }

    @Test
    fun blankModelUsesTheProviderDefault() {
        val settings = AiAssistantSettings(groqModel = "llama-custom")

        assertEquals("llama-custom", settings.modelFor(AiProvider.GROQ))
        AiProvider.entries.filter { it != AiProvider.GROQ }.forEach { provider ->
            assertEquals(provider.defaultModel, settings.modelFor(provider))
        }
    }

    @Test
    fun webSearchNeedsTheTavilyKey() {
        assertFalse(AiAssistantSettings(webSearchEnabled = true).isWebSearchActive)
        assertTrue(AiAssistantSettings(webSearchEnabled = true, tavilyApiKey = "t").isWebSearchActive)
        assertFalse(AiAssistantSettings(webSearchEnabled = false, tavilyApiKey = "t").isWebSearchActive)
    }

    @Test
    fun syncPayloadRoundTripsAndToleratesUnknownProviders() {
        val settings = AiAssistantSettings(
            enabled = true,
            provider = AiProvider.OPENROUTER,
            openRouterApiKey = "key",
            tavilyApiKey = "tavily",
        )

        assertEquals(settings, AiAssistantRepository.decode(AiAssistantRepository.encode(settings)))

        val future = AiAssistantRepository.decode("""{"enabled":true,"provider":"SOMETHING_NEW","groqApiKey":"g","extra":1}""")
        assertNotNull(future)
        assertEquals(AiProvider.CEREBRAS, future.provider)
        assertEquals("g", future.groqApiKey)
    }

    @Test
    fun metadataQuestionsSkipTheWebSearch() {
        assertFalse(AiWebSearchService.shouldSearchWeb("Vale la pena guardarlo? Senza spoiler"))
        assertFalse(AiWebSearchService.shouldSearchWeb("Consigliami titoli simili"))
        assertFalse(AiWebSearchService.shouldSearchWeb("Is it worth watching? No spoilers"))
        assertFalse(AiWebSearchService.shouldSearchWeb("ciao"))
        assertTrue(AiWebSearchService.shouldSearchWeb("Ci sarà una seconda stagione?"))
    }
}
