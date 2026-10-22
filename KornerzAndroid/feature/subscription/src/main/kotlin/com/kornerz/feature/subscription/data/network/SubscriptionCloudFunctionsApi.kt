package com.kornerz.feature.subscription.data.network

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud Functions API wrapper for server-side purchase verification.
 *
 * Modeled line-for-line on P6's
 * `feature/profile/.../data/network/CloudFunctionsApi.kt` (the
 * `deleteUserAccount` callable): same class shape, same
 * `runCatching { … await(); … }` body, same `Result`-style return, same
 * `@Singleton` constructor injection.
 *
 * [FirebaseFunctions] is already provided as a singleton by
 * `core/network/.../di/FirebaseModule.kt:39-41`, so **no new Hilt
 * provider is added** for this class.
 *
 * The `verifySubscription` function is **external to this repository** —
 * see the `subscription-verification` spec. When it is absent, this
 * returns `Result.failure` and the app degrades gracefully: the
 * optimistically-granted local entitlement stands and
 * `reconcileEntitlement()` leaves local state untouched.
 */
@Singleton
class SubscriptionCloudFunctionsApi @Inject constructor(
    private val functions: FirebaseFunctions,
) {

    /**
     * Asks the `verifySubscription` callable for the authoritative
     * verdict on a purchase.
     *
     * @param token the Play purchase token.
     * @param productId the Play product id, i.e.
     *   `SubscriptionProductType.rawValue`.
     * @return `Result.success` with the parsed verdict, or
     *   `Result.failure` when the function is not deployed, the network
     *   is unavailable, or the response does not match the contract.
     *   No exception escapes the API boundary.
     */
    suspend fun verifyPurchase(
        token: String,
        productId: String,
    ): Result<SubscriptionVerificationResult> = runCatching {
        val data = functions
            .getHttpsCallable(VERIFY_SUBSCRIPTION_FUNCTION)
            .call(
                mapOf(
                    REQUEST_KEY_TOKEN to token,
                    REQUEST_KEY_PRODUCT_ID to productId,
                ),
            )
            .await()
        Json.decodeFromString<SubscriptionVerificationResult>(data.data as String)
    }

    private companion object {
        const val VERIFY_SUBSCRIPTION_FUNCTION = "verifySubscription"
        const val REQUEST_KEY_TOKEN = "token"
        const val REQUEST_KEY_PRODUCT_ID = "productId"
    }
}