package com.pip.shared.network.http

import io.ktor.client.HttpClientConfig

/**
 * Installs the platform's on-device network inspector (Pluto's Ktor plugin on Android; a
 * no-op on iOS, where Pulse hooks `URLSession` directly instead of this Ktor client — see
 * `iosApp/Pip/PipApp.swift`) into every request [KtorHttpClient] makes. An expect/actual
 * hook so commonMain never depends on a platform-only debugging library.
 */
expect fun HttpClientConfig<*>.installNetworkDebugTools()
