package com.pip.shared.viewmodel

import com.pip.shared.core.result.PipResult
import com.pip.shared.data.repository.SettingsRepository
import com.pip.shared.memory.ConversationCache
import com.pip.shared.network.dto.SettingsUpdateDto
import com.pip.shared.network.dto.UserSettingsDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Backs the redesigned Settings screen (buddy/personality, voice & reaction
 * toggles, speaking speed, theme/brand color, privacy) on both platforms —
 * same convention as [ProfileViewModel]: each method returns the outcome
 * directly (no Flow-collection needed for a one-shot result), and also
 * updates [settings] for any UI observing it reactively. Distinct from
 * [ProfileViewModel]'s learned Adaptive Conversation Engine preferences.
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val conversationCache: ConversationCache,
) {
    private val _settings = MutableStateFlow<UserSettingsDto?>(null)
    val settings: StateFlow<UserSettingsDto?> = _settings.asStateFlow()

    suspend fun load(): PipResult<UserSettingsDto> = repository.getSettings().onSuccess { _settings.value = it }

    suspend fun setBuddy(buddy: String): PipResult<UserSettingsDto> = update(SettingsUpdateDto(buddy = buddy))

    suspend fun setPersonality(personality: String): PipResult<UserSettingsDto> =
        update(SettingsUpdateDto(personality = personality))

    suspend fun toggleVoiceReplies(): PipResult<UserSettingsDto> =
        update(SettingsUpdateDto(voiceReplies = _settings.value?.voiceReplies?.not() ?: true))

    suspend fun toggleAnimatedReactions(): PipResult<UserSettingsDto> =
        update(SettingsUpdateDto(animatedReactions = _settings.value?.animatedReactions?.not() ?: true))

    suspend fun toggleHaptics(): PipResult<UserSettingsDto> =
        update(SettingsUpdateDto(haptics = _settings.value?.haptics?.not() ?: true))

    suspend fun setSpeakingSpeed(speed: Int): PipResult<UserSettingsDto> =
        update(SettingsUpdateDto(speakingSpeed = speed))

    suspend fun setTheme(theme: String): PipResult<UserSettingsDto> = update(SettingsUpdateDto(theme = theme))

    suspend fun setBrandColor(hex: String): PipResult<UserSettingsDto> = update(SettingsUpdateDto(brandColor = hex))

    suspend fun toggleStoreConversations(): PipResult<UserSettingsDto> =
        update(SettingsUpdateDto(storeConversations = _settings.value?.storeConversations?.not() ?: true))

    /** Clears the local scrollback cache only — there's no server-side
     * "delete this account's message history" endpoint today, so this
     * can't reach further than the device's own cached copy. */
    fun clearHistory() = conversationCache.clear()

    private suspend fun update(patch: SettingsUpdateDto): PipResult<UserSettingsDto> =
        repository.updateSettings(patch).onSuccess { _settings.value = it }
}
