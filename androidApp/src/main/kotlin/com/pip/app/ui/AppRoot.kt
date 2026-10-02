package com.pip.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pip.app.ui.auth.LoginScreen
import com.pip.app.ui.auth.SignupScreen
import com.pip.app.ui.main.MainScreen
import com.pip.app.ui.onboarding.OnboardingScreen
import com.pip.app.ui.splash.SplashScreen
import com.pip.shared.data.repository.AuthRepository
import org.koin.mp.KoinPlatform

/** Mirrors iOS's `ContentView.swift` hand-rolled `AppStage` enum (PRD §4.7/§13a). */
private enum class AppStage { SPLASH, ONBOARDING, LOGIN, SIGNUP, MAIN }

@Composable
fun AppRoot() {
    var stage by remember { mutableStateOf(AppStage.SPLASH) }

    // `MainActivity.enableEdgeToEdge()` draws behind the system bars —
    // SwiftUI insets from safe areas automatically, but Compose needs this
    // applied explicitly, or toolbars/input fields end up drawn under the
    // status bar / gesture nav bar (no iOS equivalent bug, Android-only fix).
    Crossfade(
        targetState = stage,
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        animationSpec = tween(300),
        label = "app-stage",
    ) { current ->
        when (current) {
            AppStage.SPLASH ->
                SplashScreen(
                    onFinished = {
                        // Local-only check (KeyValueStore, no network) —
                        // already logged in skips both Onboarding and Login.
                        val isLoggedIn = KoinPlatform.getKoin().get<AuthRepository>().isLoggedIn()
                        stage = if (isLoggedIn) AppStage.MAIN else AppStage.ONBOARDING
                    },
                )
            AppStage.ONBOARDING -> OnboardingScreen(onFinished = { stage = AppStage.LOGIN })
            AppStage.LOGIN ->
                LoginScreen(
                    onLoggedIn = { stage = AppStage.MAIN },
                    onGoToSignup = { stage = AppStage.SIGNUP },
                )
            AppStage.SIGNUP ->
                SignupScreen(
                    onSignedUp = { stage = AppStage.MAIN },
                    onGoToLogin = { stage = AppStage.LOGIN },
                )
            AppStage.MAIN -> MainScreen()
        }
    }
}
