package com.kornerz.feature.subscription.data.network

import kotlinx.serialization.Serializable

/**
 * The `verifySubscription` HTTPS-callable's authoritative verdict.
 *
 * Computed **server-side** via the Google Play Developer API — the
 * client's own view of a purchase is never authoritative, because the
 * client cannot verify itself. This DTO is the whole Android-side half of
 * that contract.
 *
 * The Cloud Function itself is **external to this repository**: there is
 * no `functions/` directory, no `*.ts` backend source, and no
 * `firebase.json` anywhere here. This mirrors the P6
 * `deleteUserAccount` precedent, where `feature/profile`'s
 * `CloudFunctionsApi` ships the client while the function lives alongside
 * the iOS infrastructure. The `subscription-verification` spec carries the
 * full request/response contract for whoever deploys it.
 *
 * @param expiryDate ISO-8601 instant from the Play Developer API's
 *   `expiryTimeMillis`, or `null` when the API reports no expiry.
 * @param isActive the server's verdict on whether the entitlement is
 *   currently valid. `false` drives [SubscriptionFirestoreApi.clearSubscription]
 *   on the client during reconciliation.
 * @param autoRenewing `autoRenewing` from the Play subscription record.
 * @param acknowledged Play's acknowledgement state for the token.
 */
@Serializable
data class SubscriptionVerificationResult(
    val expiryDate: String? = null,
    val isActive: Boolean = false,
    val autoRenewing: Boolean? = null,
    val acknowledged: Boolean? = null,
)