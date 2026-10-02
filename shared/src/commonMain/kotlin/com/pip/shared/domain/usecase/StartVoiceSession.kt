package com.pip.shared.domain.usecase

import com.pip.shared.data.repository.VoiceRepository
import com.pip.shared.voice.VoiceSession

class StartVoiceSession(private val voiceRepository: VoiceRepository) {
    operator fun invoke(): VoiceSession = voiceRepository.startSession()
}
