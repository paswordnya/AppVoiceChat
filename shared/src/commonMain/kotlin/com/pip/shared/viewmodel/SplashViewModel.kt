package com.pip.shared.viewmodel

import com.pip.shared.data.repository.AuthRepository
import com.pip.shared.data.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The missing screen ViewModel identified in PRD §4.3 — today this logic
 * lives inline in iOS's `ContentView`'s hand-rolled `AppStage` enum.
 * Startup-time NFR (§16): resolving [isReady]/[isLoggedIn] must not block on
 * network — [AuthRepository.isLoggedIn] is a local `KeyValueStore` read, not
 * a call to the backend.
 */
class SplashViewModel(
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
) {
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /** True skips both Onboarding and Login, landing straight on Main. */
    val isLoggedIn: Boolean
        get() = authRepository.isLoggedIn()

    fun onAppear() {
        sessionRepository.sessionId // lazy local-only creation, no network (§16)
        _isReady.value = true
    }
}
