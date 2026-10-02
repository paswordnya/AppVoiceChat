package com.pip.shared.domain.model

data class Conversation(
    val sessionId: SessionId,
    val messages: List<Message>,
)
