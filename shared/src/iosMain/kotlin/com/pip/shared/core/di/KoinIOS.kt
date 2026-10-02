package com.pip.shared.core.di

import com.pip.shared.core.config.BuildVariant
import org.koin.core.context.startKoin

/**
 * iOS-facing bootstrap (PRD-KMP-Migration-v2.md §19 Sprint 6) — a plain
 * function with Swift-friendly parameter types, rather than exposing
 * Koin's `startKoin { modules(...) }` DSL (a Kotlin lambda-with-receiver)
 * directly to Swift call sites. Call once from `PipApp.init()`, before any
 * shared-module class is constructed.
 */
fun initKoinIOS(isDebug: Boolean, apiBaseUrl: String? = null) {
    startKoin {
        modules(
            sharedModule(if (isDebug) BuildVariant.DEBUG else BuildVariant.RELEASE, apiBaseUrl),
            platformModule(),
        )
    }
}
