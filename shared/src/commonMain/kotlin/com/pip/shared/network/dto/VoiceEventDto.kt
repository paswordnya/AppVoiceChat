package com.pip.shared.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Mirrors the real WS `/ws/voice/{id}` JSON event contract audited in
 * PRD-KMP-Migration-v2.md §4.4 (from `VoiceSocketClient.swift`, not
 * invented). Parsed by manual "event"-field dispatch, matching exactly how
 * the iOS client already does it in `handleEvent(_:)` — deliberately not
 * kotlinx.serialization polymorphism, so the wire contract stays legible
 * as a straight port rather than an abstraction the server doesn't share.
 */
sealed interface VoiceServerEvent {
    data class Transcript(val text: String, val final: Boolean) : VoiceServerEvent

    data class Reply(val text: String, val final: Boolean, val requestId: String?) : VoiceServerEvent

    data class QueueUpdate(val queueLength: Int) : VoiceServerEvent

    data object Interrupt : VoiceServerEvent

    data class Error(val message: String) : VoiceServerEvent

    data class Notice(val text: String) : VoiceServerEvent

    data class State(val state: String) : VoiceServerEvent

    data class Pong(val clientSentAt: Long, val serverEpochMs: Long) : VoiceServerEvent

    data class ReplyAudioStart(val turnId: String?, val serverEpochMs: Long?) : VoiceServerEvent

    data class Unknown(val event: String) : VoiceServerEvent
}

object VoiceEventParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): VoiceServerEvent? {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val event = obj["event"]?.jsonPrimitive?.contentOrNull ?: return null
        return when (event) {
            "transcript" ->
                VoiceServerEvent.Transcript(
                    text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    final = obj["final"]?.jsonPrimitive?.booleanOrNull ?: false,
                )
            "reply" ->
                VoiceServerEvent.Reply(
                    text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    final = obj["final"]?.jsonPrimitive?.booleanOrNull ?: false,
                    requestId = obj["request_id"]?.jsonPrimitive?.contentOrNull,
                )
            "queue_update" ->
                VoiceServerEvent.QueueUpdate(
                    queueLength = obj["queue_length"]?.jsonPrimitive?.intOrNull ?: 0,
                )
            "interrupt" -> VoiceServerEvent.Interrupt
            "error" ->
                VoiceServerEvent.Error(
                    obj["message"]?.jsonPrimitive?.contentOrNull ?: "Something went wrong.",
                )
            "notice" -> obj["text"]?.jsonPrimitive?.contentOrNull?.let { VoiceServerEvent.Notice(it) }
            "state" -> obj["state"]?.jsonPrimitive?.contentOrNull?.let { VoiceServerEvent.State(it) }
            "pong" -> {
                val clientSentAt = obj["client_sent_at"]?.jsonPrimitive?.longOrNull ?: return null
                val serverEpochMs = obj["server_epoch_ms"]?.jsonPrimitive?.longOrNull ?: return null
                VoiceServerEvent.Pong(clientSentAt, serverEpochMs)
            }
            "reply_audio_start" ->
                VoiceServerEvent.ReplyAudioStart(
                    turnId = obj["turn_id"]?.jsonPrimitive?.contentOrNull,
                    serverEpochMs = obj["server_epoch_ms"]?.jsonPrimitive?.longOrNull,
                )
            else -> VoiceServerEvent.Unknown(event)
        }
    }
}

/**
 * Client -> server control frames (`stop`, `push_to_talk_start`/`_end`,
 * `feedback`, `ping`) — one flexible shape, matching iOS's
 * dictionary-based `sendControlEvent([String: Any])`.
 */
@Serializable
data class VoiceControlEvent(
    val event: String,
    @SerialName("request_id") val requestId: String? = null,
    val positive: Boolean? = null,
    @SerialName("client_sent_at") val clientSentAt: Long? = null,
)
