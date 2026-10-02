package com.pip.shared.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /settings` response — settings.py's `SettingsOut`. Per-user app
 * settings (buddy/personality + voice & reaction/theme/privacy prefs) —
 * distinct from [ProfileDto]'s learned Adaptive Conversation Engine
 * preferences and from the session-scoped listening/voice mode in
 * `SettingsRepository`. */
@Serializable
data class UserSettingsDto(
    val buddy: String,
    val personality: String,
    @SerialName("voice_replies") val voiceReplies: Boolean,
    @SerialName("animated_reactions") val animatedReactions: Boolean,
    val haptics: Boolean,
    @SerialName("speaking_speed") val speakingSpeed: Int,
    val theme: String,
    @SerialName("brand_color") val brandColor: String,
    @SerialName("store_conversations") val storeConversations: Boolean,
)

/** `PATCH /settings` request body — every field optional, only sent ones apply. */
@Serializable
data class SettingsUpdateDto(
    val buddy: String? = null,
    val personality: String? = null,
    @SerialName("voice_replies") val voiceReplies: Boolean? = null,
    @SerialName("animated_reactions") val animatedReactions: Boolean? = null,
    val haptics: Boolean? = null,
    @SerialName("speaking_speed") val speakingSpeed: Int? = null,
    val theme: String? = null,
    @SerialName("brand_color") val brandColor: String? = null,
    @SerialName("store_conversations") val storeConversations: Boolean? = null,
)
