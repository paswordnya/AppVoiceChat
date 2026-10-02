package com.pip.shared.domain.usecase

import com.pip.shared.data.repository.VoiceRepository

class SubmitFeedback(private val voiceRepository: VoiceRepository) {
    suspend operator fun invoke(
        requestId: String,
        positive: Boolean,
    ) = voiceRepository.submitFeedback(requestId, positive)
}
