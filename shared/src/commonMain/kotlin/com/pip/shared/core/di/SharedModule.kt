package com.pip.shared.core.di

import com.pip.shared.ai.ModelRegistry
import com.pip.shared.ai.OfflineRouter
import com.pip.shared.ai.ProviderRegistry
import com.pip.shared.analytics.AnalyticsEmitter
import com.pip.shared.analytics.NoOpAnalyticsEmitter
import com.pip.shared.core.config.AppConfig
import com.pip.shared.core.config.BuildVariant
import com.pip.shared.core.featureflag.FeatureFlagProvider
import com.pip.shared.core.featureflag.InMemoryFeatureFlagProvider
import com.pip.shared.core.storage.KeyValueStore
import com.pip.shared.data.repository.AuthRepository
import com.pip.shared.data.repository.ChatRepository
import com.pip.shared.data.repository.ProfileRepository
import com.pip.shared.data.repository.SessionRepository
import com.pip.shared.data.repository.SettingsRepository
import com.pip.shared.data.repository.VoiceRepository
import com.pip.shared.domain.usecase.GetChatHistory
import com.pip.shared.domain.usecase.Login
import com.pip.shared.domain.usecase.SendAudioFrame
import com.pip.shared.domain.usecase.SendChatMessage
import com.pip.shared.domain.usecase.SendPushToTalkEnd
import com.pip.shared.domain.usecase.SendPushToTalkStart
import com.pip.shared.domain.usecase.SendVoicePing
import com.pip.shared.domain.usecase.SetListeningMode
import com.pip.shared.domain.usecase.SetVoiceMode
import com.pip.shared.domain.usecase.Signup
import com.pip.shared.domain.usecase.StartVoiceSession
import com.pip.shared.domain.usecase.StopVoiceSession
import com.pip.shared.domain.usecase.SubmitFeedback
import com.pip.shared.memory.ConversationCache
import com.pip.shared.memory.OfflineOutbox
import com.pip.shared.network.http.KtorHttpClient
import com.pip.shared.viewmodel.AccountViewModel
import com.pip.shared.viewmodel.AuthViewModel
import com.pip.shared.viewmodel.ChatSessionViewModel
import com.pip.shared.viewmodel.MainViewModel
import com.pip.shared.viewmodel.OnboardingViewModel
import com.pip.shared.viewmodel.ProfileViewModel
import com.pip.shared.viewmodel.SettingsViewModel
import com.pip.shared.viewmodel.SplashViewModel
import com.pip.shared.viewmodel.VoiceSessionViewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * DI wiring for everything in `commonMain` (PRD §5.2's `core/di/`). Each
 * platform supplies [platformModule] (`androidMain`/`iosMain` `actual`)
 * for platform-specific bindings ([KeyValueStore]'s backing store, `Logger`'s
 * underlying sink). `ReconnectingWebSocketClient`/`ChatSession`/`VoiceSession`
 * are deliberately not singletons here — [ChatRepository]/[VoiceRepository]
 * own their own connection lifecycle (PRD §7/§8).
 *
 * @param apiBaseUrl Override for [AppConfig.default]'s hardcoded
 * `http://localhost:8000` (PRD §4.4's real, dev-only default). Needed on
 * Android: the emulator's own `localhost` doesn't reach the host
 * machine — the host's loopback is only reachable at `10.0.2.2` from
 * inside the AVD. iOS simulator's `localhost` needs no such override.
 */
fun sharedModule(
    buildVariant: BuildVariant,
    apiBaseUrl: String? = null,
): Module =
    module {
        single { apiBaseUrl?.let { AppConfig(apiBaseUrl = it, buildVariant = buildVariant) } ?: AppConfig.default(buildVariant) }
        single<FeatureFlagProvider> { InMemoryFeatureFlagProvider() }
        single<AnalyticsEmitter> { NoOpAnalyticsEmitter() }

        single { KeyValueStore() }
        single { KtorHttpClient(get(), get()) }

        single { ConversationCache() }
        single { OfflineOutbox() }

        single { ProviderRegistry() }
        single { ModelRegistry() }
        single { OfflineRouter(get(), get()) }

        single { SessionRepository(get()) }
        single { AuthRepository(get(), get()) }
        single { ChatRepository(get(), get(), get(), get(), get()) }
        single { VoiceRepository(get(), get(), get()) }
        single { SettingsRepository(get(), get()) }
        single { ProfileRepository(get()) }

        factory { GetChatHistory(get()) }
        factory { SendChatMessage(get(), get(), get(), get()) }
        factory { StartVoiceSession(get()) }
        factory { StopVoiceSession(get()) }
        factory { SendAudioFrame(get()) }
        factory { SubmitFeedback(get()) }
        factory { SetListeningMode(get()) }
        factory { SetVoiceMode(get()) }
        factory { SendPushToTalkStart(get()) }
        factory { SendPushToTalkEnd(get()) }
        factory { SendVoicePing(get()) }
        factory { Login(get()) }
        factory { Signup(get()) }

        factory { SplashViewModel(get(), get()) }
        factory { OnboardingViewModel() }
        factory { MainViewModel() }
        factory { VoiceSessionViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
        factory { ChatSessionViewModel(get(), get(), get(), get()) }
        factory { AuthViewModel(get(), get()) }
        factory { ProfileViewModel(get()) }
        factory { SettingsViewModel(get(), get()) }
        factory { AccountViewModel(get()) }
    }

/** Platform-specific bindings — `androidMain`/`iosMain` provide the `actual`. */
expect fun platformModule(): Module
