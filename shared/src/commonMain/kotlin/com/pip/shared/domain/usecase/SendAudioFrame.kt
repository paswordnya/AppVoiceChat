package com.pip.shared.domain.usecase

import com.pip.shared.data.repository.VoiceRepository

class SendAudioFrame(private val voiceRepository: VoiceRepository) {
    suspend operator fun invoke(bytes: ByteArray) = voiceRepository.sendAudioFrame(bytes)
}
