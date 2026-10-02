package com.pip.shared.ai

/**
 * Offline-capable providers only — Ollama on localhost, LM Studio on LAN
 * (PRD §5a/§9/§21). Cloud providers (Gemini/OpenAI/Claude/Groq/OpenRouter)
 * stay server-side and never appear here; the client never holds a cloud
 * API key.
 */
data class OfflineProvider(
    val id: String,
    val displayName: String,
    val baseUrl: String,
)

class ProviderRegistry {
    private val providers = mutableListOf<OfflineProvider>()

    fun register(provider: OfflineProvider) {
        if (providers.none { it.id == provider.id }) providers.add(provider)
    }

    fun all(): List<OfflineProvider> = providers.toList()

    fun isEmpty(): Boolean = providers.isEmpty()
}
