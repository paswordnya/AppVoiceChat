package com.pip.shared.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /profile` response — auth.py's ProfileOut (PRD_TDD_pip_Voice_AI.md §23). */
@Serializable
data class ProfileDto(
    @SerialName("user_id") val userId: Int,
    @SerialName("preferred_language") val preferredLanguage: String? = null,
    @SerialName("communication_style") val communicationStyle: String? = null,
    @SerialName("preferred_tone") val preferredTone: String? = null,
    @SerialName("preferred_response_length") val preferredResponseLength: String? = null,
    @SerialName("technical_level") val technicalLevel: String? = null,
    @SerialName("favorite_topics") val favoriteTopics: List<String> = emptyList(),
    @SerialName("response_preference") val responsePreference: String? = null,
    @SerialName("humor_preference") val humorPreference: String? = null,
    @SerialName("emoji_preference") val emojiPreference: String? = null,
    @SerialName("interaction_score") val interactionScore: Double = 0.0,
    @SerialName("confidence_score") val confidenceScore: Double = 0.0,
    @SerialName("locked_fields") val lockedFields: List<String> = emptyList(),
    @SerialName("last_updated_at") val lastUpdatedAt: String,
)

/** `PATCH /profile` request body — every field optional, only sent ones apply. */
@Serializable
data class ProfileUpdateDto(
    @SerialName("preferred_language") val preferredLanguage: String? = null,
    @SerialName("communication_style") val communicationStyle: String? = null,
    @SerialName("preferred_tone") val preferredTone: String? = null,
    @SerialName("preferred_response_length") val preferredResponseLength: String? = null,
    @SerialName("technical_level") val technicalLevel: String? = null,
    @SerialName("humor_preference") val humorPreference: String? = null,
    @SerialName("emoji_preference") val emojiPreference: String? = null,
)

/** `GET /profile/history` entry — one recompute snapshot. */
@Serializable
data class ProfileHistoryEntryDto(
    @SerialName("communication_style") val communicationStyle: String? = null,
    @SerialName("preferred_tone") val preferredTone: String? = null,
    @SerialName("preferred_response_length") val preferredResponseLength: String? = null,
    @SerialName("technical_level") val technicalLevel: String? = null,
    @SerialName("favorite_topics") val favoriteTopics: List<String> = emptyList(),
    @SerialName("interaction_score") val interactionScore: Double = 0.0,
    @SerialName("confidence_score") val confidenceScore: Double = 0.0,
    @SerialName("recorded_at") val recordedAt: String,
)
