package com.pip.shared.network.http

import com.pluto.plugins.network.interceptors.ktor.PlutoKtorInterceptor
import io.ktor.client.HttpClientConfig

/** In release builds this resolves against `network-no-op`'s API-compatible stub instead. */
actual fun HttpClientConfig<*>.installNetworkDebugTools() {
    install(PlutoKtorInterceptor)
}
