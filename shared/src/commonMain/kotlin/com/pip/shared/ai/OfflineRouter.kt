package com.pip.shared.ai

import com.pip.shared.core.result.PipError
import com.pip.shared.core.result.PipResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The one routing decision the client makes for itself (PRD §9): is the
 * backend reachable, and if not, is there a usable offline provider on
 * this device/LAN? Explicitly not a duplicate of `voice_router.py`/
 * `ai_router.py` — those stay server-side, unmodified.
 */
sealed interface OfflineRoutingDecision {
    data class RouteToProvider(val provider: OfflineProvider, val model: OfflineModel) : OfflineRoutingDecision

    data object QueueForLater : OfflineRoutingDecision

    /** Backend is reachable — normal WS flow, server does ALL model routing. */
    data object UseServer : OfflineRoutingDecision
}

@Serializable
private data class OllamaGenerateRequest(val model: String, val prompt: String, val stream: Boolean = false)

@Serializable
private data class OllamaGenerateResponse(val response: String)

class OfflineRouter(
    private val providerRegistry: ProviderRegistry,
    private val modelRegistry: ModelRegistry,
) {
    private val client by lazy {
        HttpClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    }

    fun decide(backendReachable: Boolean): OfflineRoutingDecision {
        if (backendReachable) return OfflineRoutingDecision.UseServer

        val provider =
            providerRegistry.all().firstOrNull()
                ?: return OfflineRoutingDecision.QueueForLater
        val model =
            modelRegistry.modelsFor(provider.id).firstOrNull()
                ?: return OfflineRoutingDecision.QueueForLater

        return OfflineRoutingDecision.RouteToProvider(provider, model)
    }

    /**
     * Calls an Ollama-compatible `/api/generate` endpoint directly (PRD
     * §21: Ollama's native shape needs no API key, matching §9's "the
     * client never holds a cloud provider API key"). LM Studio's
     * OpenAI-compatible endpoint is a different request/response shape —
     * out of scope for this pass, flagged rather than half-implemented.
     */
    suspend fun generate(
        provider: OfflineProvider,
        model: OfflineModel,
        prompt: String,
    ): PipResult<String> =
        try {
            val response: OllamaGenerateResponse =
                client.post("${provider.baseUrl}/api/generate") {
                    contentType(ContentType.Application.Json)
                    setBody(OllamaGenerateRequest(model = model.modelId, prompt = prompt))
                }.body()
            PipResult.Success(response.response)
        } catch (e: Exception) {
            PipResult.Failure(PipError.NoConnectivity)
        }
}
