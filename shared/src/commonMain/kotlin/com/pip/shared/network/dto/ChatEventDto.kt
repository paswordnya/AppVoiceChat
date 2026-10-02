package com.pip.shared.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Mirrors the real WS `/ws/chat/{id}` JSON event contract audited in
 * PRD-KMP-Migration-v2.md §4.4 (from `ChatSocketClient.swift`).
 */
sealed interface ChatServerEvent {
    data object ThinkingStarted : ChatServerEvent

    data class Token(val text: String) : ChatServerEvent

    data class Done(val text: String, val model: String) : ChatServerEvent

    data class Command(val text: String) : ChatServerEvent

    data class Error(val message: String) : ChatServerEvent

    data class Notice(val text: String) : ChatServerEvent
}

object ChatEventParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): ChatServerEvent? {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val event = obj["event"]?.jsonPrimitive?.contentOrNull ?: return null
        return when (event) {
            "thinking" -> ChatServerEvent.ThinkingStarted
            "typing" -> obj["text"]?.jsonPrimitive?.contentOrNull?.let { ChatServerEvent.Token(it) }
            "done" ->
                ChatServerEvent.Done(
                    text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    model = obj["model"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            "command" -> ChatServerEvent.Command(obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
            "error" ->
                ChatServerEvent.Error(
                    obj["message"]?.jsonPrimitive?.contentOrNull ?: "Something went wrong.",
                )
            "notice" -> obj["text"]?.jsonPrimitive?.contentOrNull?.let { ChatServerEvent.Notice(it) }
            else -> null
        }
    }
}

/** Outgoing chat message — matches iOS's `ChatSocketClient.send(message:...)` payload. */
@Serializable
data class ChatOutgoingMessage(
    val message: String,
    @SerialName("reply_sender_label") val replySenderLabel: String? = null,
    @SerialName("reply_snippet") val replySnippet: String? = null,
)
