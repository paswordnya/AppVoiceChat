package com.pip.shared.viewmodel

import com.pip.shared.core.result.PipResult
import com.pip.shared.data.repository.ProfileRepository
import com.pip.shared.network.dto.ProfileDto
import com.pip.shared.network.dto.ProfileHistoryEntryDto
import com.pip.shared.network.dto.ProfileUpdateDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Backs the Adaptive Conversation Engine's profile screen on both
 * platforms (PRD_TDD_pip_Voice_AI.md §23.4). Each method returns
 * `PipResult` directly (same convention as `AuthViewModel`'s
 * login/signup) so callers get a definitive outcome without needing Flow
 * collection just for a one-shot result; [profile] is also cached here for
 * screens that just want the current snapshot without re-fetching.
 */
class ProfileViewModel(private val repository: ProfileRepository) {
    private val _profile = MutableStateFlow<ProfileDto?>(null)
    val profile: StateFlow<ProfileDto?> = _profile.asStateFlow()

    suspend fun load(): PipResult<ProfileDto> =
        repository.getProfile().onSuccess { _profile.value = it }

    suspend fun update(update: ProfileUpdateDto): PipResult<ProfileDto> =
        repository.updateProfile(update).onSuccess { _profile.value = it }

    suspend fun reset(): PipResult<ProfileDto> =
        repository.resetProfile().onSuccess { _profile.value = it }

    suspend fun delete(): PipResult<Unit> =
        repository.deleteProfile().onSuccess { _profile.value = null }

    suspend fun loadHistory(limit: Int = 50): PipResult<List<ProfileHistoryEntryDto>> =
        repository.getProfileHistory(limit)
}
