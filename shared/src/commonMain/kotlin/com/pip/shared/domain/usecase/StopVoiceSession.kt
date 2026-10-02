package com.pip.shared.domain.usecase

import com.pip.shared.data.repository.VoiceRepository

class StopVoiceSession(private val voiceRepository: VoiceRepository) {
    operator fun invoke() = voiceRepository.stopSession()
}
