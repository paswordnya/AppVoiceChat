package com.pip.shared.network.http

import io.ktor.client.HttpClientConfig

/**
 * Nothing to install here — Pulse's `URLSessionProxyDelegate.enableAutomaticRegistration()`
 * (called once from `PipApp.swift`) swizzles `URLSession` itself, so it already captures
 * this client's Darwin-engine traffic without any per-client Ktor plugin.
 */
actual fun HttpClientConfig<*>.installNetworkDebugTools() {
}
