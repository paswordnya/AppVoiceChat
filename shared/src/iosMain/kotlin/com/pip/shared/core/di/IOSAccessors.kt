package com.pip.shared.core.di

import com.pip.shared.viewmodel.AccountViewModel
import com.pip.shared.viewmodel.AuthViewModel
import com.pip.shared.viewmodel.ChatSessionViewModel
import com.pip.shared.viewmodel.ProfileViewModel
import com.pip.shared.viewmodel.SettingsViewModel
import com.pip.shared.viewmodel.SplashViewModel
import com.pip.shared.viewmodel.VoiceSessionViewModel
import org.koin.mp.KoinPlatform

/**
 * Swift-friendly Koin accessors (PRD §19 Sprint 6) — keeps Koin's own API
 * off the Swift call sites, matching [initKoinIOS]'s reasoning. Requires
 * [initKoinIOS] to have run first.
 */
fun getChatSessionViewModel(): ChatSessionViewModel = KoinPlatform.getKoin().get()

fun getVoiceSessionViewModel(): VoiceSessionViewModel = KoinPlatform.getKoin().get()

fun getSplashViewModel(): SplashViewModel = KoinPlatform.getKoin().get()

fun getAuthViewModel(): AuthViewModel = KoinPlatform.getKoin().get()

fun getProfileViewModel(): ProfileViewModel = KoinPlatform.getKoin().get()

fun getSettingsViewModel(): SettingsViewModel = KoinPlatform.getKoin().get()

fun getAccountViewModel(): AccountViewModel = KoinPlatform.getKoin().get()
