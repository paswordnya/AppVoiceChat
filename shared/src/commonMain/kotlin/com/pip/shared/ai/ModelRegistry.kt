package com.pip.shared.ai

/** Local catalog of offline-capable models only (PRD §5a/§9) — feeds [OfflineRouter]. */
data class OfflineModel(
    val providerId: String,
    val modelId: String,
    val displayName: String,
)

class ModelRegistry {
    private val models = mutableListOf<OfflineModel>()

    fun register(model: OfflineModel) {
        models.add(model)
    }

    fun modelsFor(providerId: String): List<OfflineModel> = models.filter { it.providerId == providerId }
}
