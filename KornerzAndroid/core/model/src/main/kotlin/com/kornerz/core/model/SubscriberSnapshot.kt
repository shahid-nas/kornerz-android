package com.kornerz.core.model

/**
 * UI-facing snapshot of one remote subscriber in a [CallSession].
 *
 * The repository projects the Vonage subscriber (held by
 * `VonageSessionController`) into this lightweight model so the
 * UI layer never sees the SDK directly (arch §2.5).
 *
 * NOT `@Serializable` — no persistence this phase.
 */
data class SubscriberSnapshot(
    val streamId: String,
    val subscriberName: String?,
    val hasVideo: Boolean,
    /** Avatar URL published by the remote peer (iOS `PublisherInfo.image`). */
    val thumbnailUrl: String? = null,
    /** Whether the remote peer currently has their hand raised. */
    val raisedHand: Boolean = false,
)
