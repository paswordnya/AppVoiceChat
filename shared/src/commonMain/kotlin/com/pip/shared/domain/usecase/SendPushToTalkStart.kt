package com.pip.shared.domain.usecase

import com.pip.shared.data.repository.VoiceRepository

class SendPushToTalkStart(private val voiceRepository: VoiceRepository) {
    operator fun invoke() = voiceRepository.sendPushToTalkStart()
}
