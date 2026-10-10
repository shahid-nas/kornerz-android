package com.kornerz.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Nook (Android) / Event (iOS) record.
 *
 * The P3 schema fields (`communityId`, `title`, `startDate`, `isLive`)
 * are kept as primary constructor parameters so the existing
 * `KornerFirestoreApi` and `HomeFirestoreApi` mappers keep working.
 *
 * The iOS schema fields (`eventCommunityId`, `eventTitle`,
 * `eventDateInit`, `eventDateFinish`, `eventType`, `sessionId`,
 * `publisherId`) are added as optional properties so the new
 * `NookFirestoreApi` can populate them when reading the iOS top-level
 * `Events` collection. UI code that needs iOS fields can opt in.
 *
 * Mirrors the iOS `Event.CodingKeys` for the new fields so the
 * same Firestore documents are consumable by both apps.
 */
@Serializable
data class Nook(
    val id: String,
    @SerialName("communityId") val communityId: String,
    val title: String,
    @SerialName("firebaseImageUrl") val coverUrl: String? = null,
    @SerialName("startDate") val startDate: Instant,
    @SerialName("isLive") val isLive: Boolean = false,
    // iOS Event parity (optional, populated by NookFirestoreApi when
    // reading the top-level `Events` collection).
    @SerialName("eventCommunityId") val eventCommunityId: String? = null,
    @SerialName("eventTitle") val eventTitle: String? = null,
    @SerialName("eventDateInit") val eventDateInit: Instant? = null,
    @SerialName("eventDateFinish") val eventDateFinish: Instant? = null,
    @SerialName("eventType") val type: String? = null,
    @SerialName("sessionId") val sessionId: String? = null,
    @SerialName("publisherId") val publisherId: String? = null,
)
