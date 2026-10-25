package com.kornerz.core.network.videocall

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire format for the publisher identity that travels with a Vonage
 * stream's `name` field. 1:1 port of iOS `PublisherModel`
 * (`kornerz/Kornerz/Models/PublisherModel.swift`).
 *
 * iOS publishes `PublisherModel.currentUserInfo.toJSON()` as the
 * OpenTok publisher name, and every remote peer parses the stream name
 * back into the same struct to render the participant's display name and
 * avatar. The JSON keys are therefore part of the **cross-platform
 * protocol** — an Android publisher must emit exactly
 * `{"userName":…,"userId":…,"image":…}` so an iOS subscriber can read
 * it, and vice versa.
 *
 * Android's `Publisher.Builder.name(json)` carries the same payload, so
 * iOS and Android participants can join the same room and still label
 * each other correctly.
 */
@Serializable
data class PublisherInfo(
    @SerialName("userName") val userName: String,
    @SerialName("userId") val userId: String,
    @SerialName("image") val image: String? = null,
) {

    /** Serialised payload used as the Vonage stream name. */
    fun toJson(): String = Json.encodeToString(PublisherInfo.serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Parses a Vonage stream name back into a [PublisherInfo].
         * Returns `null` when the stream name is not a Kornerz publisher
         * payload (e.g. a third-party client that publishes a bare
         * string), so callers can fall back to the raw stream name.
         */
        fun fromJson(raw: String?): PublisherInfo? {
            if (raw.isNullOrBlank()) return null
            return runCatching { json.decodeFromString<PublisherInfo>(raw) }.getOrNull()
        }
    }
}
