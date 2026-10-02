package com.pip.shared.domain.usecase

import com.pip.shared.core.result.PipResult
import com.pip.shared.data.repository.SettingsRepository

class SetVoiceMode(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(mode: String): PipResult<Unit> = settingsRepository.setVoiceMode(mode)
}
