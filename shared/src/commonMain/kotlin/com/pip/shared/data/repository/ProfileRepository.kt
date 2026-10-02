package com.pip.shared.data.repository

import com.pip.shared.core.result.PipResult
import com.pip.shared.network.dto.ProfileDto
import com.pip.shared.network.dto.ProfileHistoryEntryDto
import com.pip.shared.network.dto.ProfileUpdateDto
import com.pip.shared.network.http.ApiEndpoint
import com.pip.shared.network.http.KtorHttpClient

/** Backs the Adaptive Conversation Engine's Privacy surface (PRD_TDD_pip_Voice_AI.md §23.4) —
 * view/edit/reset/delete, all scoped server-side to the caller's own bearer token. */
class ProfileRepository(private val httpClient: KtorHttpClient) {
    suspend fun getProfile(): PipResult<ProfileDto> =
        httpClient.request(ApiEndpoint.GetProfile)

    suspend fun updateProfile(update: ProfileUpdateDto): PipResult<ProfileDto> =
        httpClient.requestWithBody(ApiEndpoint.UpdateProfile, update)

    suspend fun resetProfile(): PipResult<ProfileDto> =
        httpClient.request(ApiEndpoint.ResetProfile)

    suspend fun deleteProfile(): PipResult<Unit> =
        httpClient.requestNoBody(ApiEndpoint.DeleteProfile)

    suspend fun getProfileHistory(limit: Int = 50): PipResult<List<ProfileHistoryEntryDto>> =
        httpClient.request(ApiEndpoint.GetProfileHistory(limit))
}
