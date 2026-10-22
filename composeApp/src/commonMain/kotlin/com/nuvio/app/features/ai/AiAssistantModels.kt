package com.nuvio.app.features.ai

import kotlinx.serialization.Serializable

internal const val AI_ASSISTANT_LOG_TAG = "AiAssistant"

@Serializable
internal enum class AiProvider(
    val displayName: String,
    val defaultModel: String,
    val keyUrl: String,
) {
    CEREBRAS("Cerebras", "gpt-oss-120b", "https://cloud.cerebras.ai/"),
    GROQ("Groq", "openai/gpt-oss-120b", "https://console.groq.com/keys"),
    // Alias that always points to the current Flash model, so a model retirement does not break the default.
    GEMINI("Gemini", "gemini-flash-latest", "https://aistudio.google.com/app/apikey"),
    OPENROUTER("OpenRouter", "openrouter/auto", "https://openrouter.ai/settings/keys"),
}

internal const val TAVILY_KEY_URL = "https://app.tavily.com/home"

/**
 * AI assistant preferences, all synced with the profile (API keys included) through
 * [AiAssistantProfileSync]. A blank model means "use [AiProvider.defaultModel]".
 */
@Serializable
internal data class AiAssistantSettings(
    val enabled: Boolean = false,
    val webSearchEnabled: Boolean = true,
    val provider: AiProvider = AiProvider.CEREBRAS,
    val tavilyApiKey: String = "",
    val cerebrasApiKey: String = "",
    val groqApiKey: String = "",
    val geminiApiKey: String = "",
    val openRouterApiKey: String = "",
    val cerebrasModel: String = "",
    val groqModel: String = "",
    val geminiModel: String = "",
    val openRouterModel: String = "",
) {
    fun apiKeyFor(provider: AiProvider): String = when (provider) {
        AiProvider.CEREBRAS -> cerebrasApiKey
        AiProvider.GROQ -> groqApiKey
        AiProvider.GEMINI -> geminiApiKey
        AiProvider.OPENROUTER -> openRouterApiKey
    }

    fun modelFor(provider: AiProvider): String = when (provider) {
        AiProvider.CEREBRAS -> cerebrasModel
        AiProvider.GROQ -> groqModel
        AiProvider.GEMINI -> geminiModel
        AiProvider.OPENROUTER -> openRouterModel
    }.ifBlank { provider.defaultModel }

    fun isConfigured(provider: AiProvider): Boolean = apiKeyFor(provider).isNotBlank()

    /** Providers to try in order: the selected one first, then every other configured one as fallback. */
    val providerChain: List<AiProvider>
        get() = (listOf(provider) + AiProvider.entries.filter { it != provider })
            .filter(::isConfigured)

    val isWebSearchActive: Boolean
        get() = webSearchEnabled && tavilyApiKey.isNotBlank()

    val isReady: Boolean
        get() = enabled && providerChain.isNotEmpty()
}

internal data class AiChatMessage(
    val role: AiChatRole,
    val text: String,
    val sources: List<AiWebSource> = emptyList(),
)

internal data class AiAssistantReply(
    val answer: String,
    val provider: AiProvider,
    val sources: List<AiWebSource> = emptyList(),
)

internal data class AiWebSource(
    val title: String,
    val url: String,
)

internal enum class AiChatRole {
    USER,
    ASSISTANT,
}
