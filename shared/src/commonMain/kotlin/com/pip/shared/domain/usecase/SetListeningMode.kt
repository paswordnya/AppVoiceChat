package com.pip.shared.domain.usecase

import com.pip.shared.core.result.PipResult
import com.pip.shared.data.repository.SettingsRepository
import com.pip.shared.voice.ListeningMode

class SetListeningMode(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(mode: ListeningMode): PipResult<Unit> = settingsRepository.setListeningMode(mode)
}
