package com.pip.shared.core.di

import android.content.Context

/**
 * The Android host app must set [applicationContext] once at startup
 * (e.g. `Application.onCreate()`) before using anything in `shared` that
 * touches local storage (PRD §5.2's `core/storage/`). Not wired through
 * `koin-android`'s `androidContext()` — there's no Android app to test
 * that integration against yet (§4.8), so this is the lightest bootstrap
 * that unblocks Phase 0/1's compile target without adding an
 * Android-only Koin extension ahead of when it's actually needed.
 */
object AndroidPlatformContext {
    lateinit var applicationContext: Context
}
