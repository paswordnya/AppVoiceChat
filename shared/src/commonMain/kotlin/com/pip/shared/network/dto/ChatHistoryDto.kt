package com.pip.shared.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /api/session/{id}/history` response — matches iOS's `ChatHistory`/`RemoteChatMessage`. */
@Serializable
data class RemoteChatMessageDto(
    val role: String,
    val content: String,
    @SerialName("reply_sender_label") val replySenderLabel: String? = null,
    @SerialName("reply_snippet") val replySnippet: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ChatHistoryDto(
    @SerialName("session_id") val sessionId: String,
    val model: String,
    val voice: String,
    val messages: List<RemoteChatMessageDto>,
)
