package com.pip.shared.domain.usecase

import com.pip.shared.core.result.PipResult
import com.pip.shared.data.repository.ChatRepository
import com.pip.shared.domain.model.Conversation

class GetChatHistory(private val chatRepository: ChatRepository) {
    suspend operator fun invoke(): PipResult<Conversation> = chatRepository.getHistory()
}
