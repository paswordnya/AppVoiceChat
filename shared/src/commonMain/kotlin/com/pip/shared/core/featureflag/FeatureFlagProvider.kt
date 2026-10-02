package com.pip.shared.core.featureflag

/**
 * Local default + remote-overridable flags (PRD §12/§16). No remote-config
 * transport exists yet — `override` is the seam a future Phase 7+ remote
 * fetch plugs into, not a working remote client today.
 */
interface FeatureFlagProvider {
    fun isEnabled(
        key: String,
        default: Boolean = false,
    ): Boolean
}

class InMemoryFeatureFlagProvider(
    private val overrides: MutableMap<String, Boolean> = mutableMapOf(),
) : FeatureFlagProvider {
    override fun isEnabled(
        key: String,
        default: Boolean,
    ): Boolean = overrides[key] ?: default

    fun setOverride(
        key: String,
        value: Boolean,
    ) {
        overrides[key] = value
    }
}
