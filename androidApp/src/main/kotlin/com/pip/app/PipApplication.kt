package com.pip.app

import android.app.Application
import com.pip.shared.core.config.BuildVariant
import com.pip.shared.core.di.AndroidPlatformContext
import com.pip.shared.core.di.platformModule
import com.pip.shared.core.di.sharedModule
import com.pluto.Pluto
import com.pluto.plugins.exceptions.PlutoExceptionsPlugin
import com.pluto.plugins.network.PlutoNetworkPlugin
import org.koin.core.context.startKoin

class PipApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidPlatformContext.applicationContext = applicationContext

        // No-op on release builds (pluto-*-noop artifacts) — safe to call unconditionally.
        // The network plugin's actual Ktor wiring lives in shared/androidMain's
        // NetworkDebugTools.android.kt, next to every KtorHttpClient request it captures.
        Pluto.Installer(this)
            .addPlugin(PlutoNetworkPlugin())
            .addPlugin(PlutoExceptionsPlugin())
            .install()

        // Floating tap target instead of only a notification — silently
        // stays hidden until the user grants "draw over other apps"
        // (MainActivity prompts for it on debug builds).
        Pluto.showNotch(true)

        startKoin {
            modules(
                sharedModule(
                    buildVariant = if (BuildConfig.DEBUG) BuildVariant.DEBUG else BuildVariant.RELEASE,
                    // `BuildConfig.BACKEND_HOST` defaults to `10.0.2.2` (the
                    // AVD's alias for the host machine's loopback) but reads
                    // an override from the gitignored root
                    // `local.properties` (`BACKEND_HOST=192.168.x.x`) for
                    // physical devices, which can't reach `10.0.2.2` at all —
                    // same caveat iOS's AppEnvironment.swift already
                    // documents for physical devices needing a LAN IP
                    // instead of `localhost`.
                    apiBaseUrl = if (BuildConfig.DEBUG) "http://${BuildConfig.BACKEND_HOST}:8000" else null,
                ),
                platformModule(),
            )
        }
    }
}
