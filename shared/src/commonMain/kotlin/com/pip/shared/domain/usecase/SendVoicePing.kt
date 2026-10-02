package com.pip.shared.domain.usecase

import com.pip.shared.data.repository.VoiceRepository

class SendVoicePing(private val voiceRepository: VoiceRepository) {
    operator fun invoke(clientSentAt: Long) = voiceRepository.sendPing(clientSentAt)
}
