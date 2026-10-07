package com.kornerz.core.storage.session

import com.kornerz.core.model.User
import kotlinx.serialization.Serializable

/**
 * On-disk shape for the persisted session. Holds the auth token, its
 * `expiresAt` (ISO-8601 UTC instant), and the JSON-serialized [User]
 * blob returned by the sign-up / login Cloud Functions.
 *
 * Encoded as a single Preferences DataStore key (see [SessionSerializer])
 * so writes are atomic.
 */
@Serializable
data class SessionRecord(
    val token: String,
    val expiresAt: String,
    val user: User,
)