package com.pip.shared.domain.usecase

import com.pip.shared.data.repository.VoiceRepository

class SendPushToTalkEnd(private val voiceRepository: VoiceRepository) {
    operator fun invoke() = voiceRepository.sendPushToTalkEnd()
}
