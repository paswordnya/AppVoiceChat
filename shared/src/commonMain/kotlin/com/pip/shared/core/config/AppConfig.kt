package com.pip.shared.core.config

/**
 * Base URL + build variant. Mirrors the real, hardcoded values in the iOS
 * app's `AppEnvironment.swift` today (PRD §4.4) — both dev/prod point at
 * `http://localhost:8000` until a real environment-plist/remote-config
 * setup exists. Not invented here; just moved to a shared home so Android
 * doesn't hand-roll its own copy on day one.
 */
enum class BuildVariant { DEBUG, RELEASE }

data class AppConfig(
    val apiBaseUrl: String,
    val buildVariant: BuildVariant,
) {
    /** `http(s)://` -> `ws(s)://`, same derivation as `AppEnvironment.wsBaseURL`. */
    val wsBaseUrl: String
        get() =
            when {
                apiBaseUrl.startsWith("https://") -> "wss://" + apiBaseUrl.removePrefix("https://")
                apiBaseUrl.startsWith("http://") -> "ws://" + apiBaseUrl.removePrefix("http://")
                else -> apiBaseUrl
            }

    companion object {
        fun default(buildVariant: BuildVariant): AppConfig = AppConfig(apiBaseUrl = "http://localhost:8000", buildVariant = buildVariant)
    }
}
