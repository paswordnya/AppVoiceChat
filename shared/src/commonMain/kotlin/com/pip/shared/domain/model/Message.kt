package com.pip.shared.domain.model

enum class MessageRole { USER, ASSISTANT }

data class Message(
    val id: String,
    val role: MessageRole,
    val content: String,
    val replySenderLabel: String? = null,
    val replySnippet: String? = null,
    val createdAt: String,
)
