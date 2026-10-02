package com.pip.shared.data.repository

import com.pip.shared.core.result.PipResult
import com.pip.shared.network.dto.SettingsUpdateDto
import com.pip.shared.network.dto.UserSettingsDto
import com.pip.shared.network.http.ApiEndpoint
import com.pip.shared.network.http.KtorHttpClient
import com.pip.shared.voice.ListeningMode

/** Backs `SetListeningMode`/`SetVoiceMode` UseCases (PRD §6 Phase 3, per-session)
 * and, since the redesign, the user's own cross-device app settings (buddy/
 * personality/voice & reaction toggles/theme/privacy — backend `/settings`,
 * distinct from [ProfileRepository]'s learned-preference concept). */
class SettingsRepository(
    private val httpClient: KtorHttpClient,
    private val sessionRepository: SessionRepository,
) {
    suspend fun setListeningMode(mode: ListeningMode): PipResult<Unit> =
        httpClient.requestNoBody(ApiEndpoint.SetListeningMode(sessionRepository.sessionId.value, mode.wireValue))

    suspend fun setVoiceMode(mode: String): PipResult<Unit> =
        httpClient.requestNoBody(ApiEndpoint.SetVoiceMode(sessionRepository.sessionId.value, mode))

    suspend fun getSettings(): PipResult<UserSettingsDto> = httpClient.request(ApiEndpoint.GetSettings)

    suspend fun updateSettings(update: SettingsUpdateDto): PipResult<UserSettingsDto> =
        httpClient.requestWithBody(ApiEndpoint.UpdateSettings, update)
}
