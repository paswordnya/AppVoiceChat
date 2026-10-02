package com.pip.shared.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** PRD §9's decision table, table-driven per §17's Testing Strategy. */
class OfflineRouterTest {
    @Test
    fun decide_backendReachable_alwaysUsesServer() {
        val router = OfflineRouter(ProviderRegistry(), ModelRegistry())
        assertEquals(OfflineRoutingDecision.UseServer, router.decide(backendReachable = true))
    }

    @Test
    fun decide_unreachable_emptyRegistry_queuesForLater() {
        val router = OfflineRouter(ProviderRegistry(), ModelRegistry())
        assertEquals(OfflineRoutingDecision.QueueForLater, router.decide(backendReachable = false))
    }

    @Test
    fun decide_unreachable_providerWithNoModel_queuesForLater() {
        val providers =
            ProviderRegistry().apply {
                register(OfflineProvider("ollama", "Ollama", "http://localhost:11434"))
            }
        val router = OfflineRouter(providers, ModelRegistry())
        assertEquals(OfflineRoutingDecision.QueueForLater, router.decide(backendReachable = false))
    }

    @Test
    fun decide_unreachable_providerAndModelConfigured_routesOffline() {
        val providers =
            ProviderRegistry().apply {
                register(OfflineProvider("ollama", "Ollama", "http://localhost:11434"))
            }
        val models =
            ModelRegistry().apply {
                register(OfflineModel("ollama", "llama3", "Llama 3"))
            }
        val router = OfflineRouter(providers, models)
        val decision = router.decide(backendReachable = false)
        assertIs<OfflineRoutingDecision.RouteToProvider>(decision)
        assertEquals("ollama", decision.provider.id)
        assertEquals("llama3", decision.model.modelId)
    }
}
