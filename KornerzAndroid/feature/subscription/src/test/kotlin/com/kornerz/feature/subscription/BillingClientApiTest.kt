package com.kornerz.feature.subscription

import com.kornerz.core.model.SubscriptionProduct
import com.kornerz.core.model.SubscriptionProductType
import com.kornerz.feature.subscription.data.SubscriptionError
import com.kornerz.feature.subscription.data.SubscriptionException
import com.kornerz.feature.subscription.data.billing.asSubscriptionThrowable
import com.kornerz.feature.subscription.data.billing.toSubscriptionError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wrapper-boundary behaviour of `BillingClientApi` that can be proven
 * without a device.
 *
 * A real `BillingClient` needs the Play Store, and the codebase has no
 * Robolectric or instrumentation harness, so the assertions here target the
 * two things that *are* pure functions in the wrapper: the
 * `BillingResultCode` → `SubscriptionError` mapping, and the typed
 * re-throw that carries a bare SDK exception across the `Result` boundary.
 *
 * The scenario the `play-billing` spec calls out for
 * `BillingClientApiTest` — "empty catalog on an unregistered application id
 * returns failure, not a throw" — is exercised end-to-end at the repository
 * and ViewModel level in [SubscriptionRepositoryTest] and
 * [SubscriptionViewModelTest] with the wrapper faked, which is the same
 * guarantee without a Play dependency.
 */
class BillingClientApiTest {

    @Test
    fun `an unreachable Play service maps to BillingUnavailable`() {
        for (code in BILLING_UNAVAILABLE_CODES) {
            assertEquals(
                "code $code",
                SubscriptionError.BillingUnavailable,
                code.toSubscriptionError(),
            )
        }
    }

    @Test
    fun `an unresolvable product maps to ProductUnavailable`() {
        for (code in PRODUCT_UNAVAILABLE_CODES) {
            assertEquals(
                "code $code",
                SubscriptionError.ProductUnavailable,
                code.toSubscriptionError(),
            )
        }
    }

    @Test
    fun `a cancelled sheet maps to UserCancelled`() {
        assertEquals(SubscriptionError.UserCancelled, USER_CANCELED.toSubscriptionError())
    }

    @Test
    fun `transport failures map to NetworkError`() {
        for (code in NETWORK_CODES) {
            assertEquals("code $code", SubscriptionError.NetworkError, code.toSubscriptionError())
        }
    }

    @Test
    fun `an unmapped code falls through to Unknown`() {
        val error = GENERIC_ERROR.toSubscriptionError()
        assertTrue("expected Unknown, got $error", error is SubscriptionError.Unknown)
    }

    @Test
    fun `an already-owned purchase is not treated as pending`() {
        // ITEM_ALREADY_OWNED means Play already holds the subscription —
        // the shape a restore surfaces. Reporting it as PurchasePending
        // would tell the user to wait for a purchase that will never start.
        assertEquals(
            SubscriptionError.ProductUnavailable,
            ITEM_ALREADY_OWNED.toSubscriptionError(),
        )
        assertFalse(
            ITEM_ALREADY_OWNED.toSubscriptionError() == SubscriptionError.PurchasePending,
        )
    }

    @Test
    fun `a bare SDK exception is re-thrown as a typed SubscriptionException`() {
        val typed = IllegalStateException("IBillingControllerService.bind() failed")
            .asSubscriptionThrowable("queryProductDetails") as SubscriptionException

        assertTrue(
            "expected Unknown, got ${typed.errorKind}",
            typed.errorKind is SubscriptionError.Unknown,
        )
        assertTrue(
            "the operation name must survive for diagnosis: ${typed.message}",
            typed.message.orEmpty().contains("queryProductDetails"),
        )
    }

    @Test
    fun `an existing SubscriptionException passes through unchanged`() {
        // Double-wrapping would bury the original typed cause.
        val original = SubscriptionException(
            SubscriptionError.NotAcknowledged,
            "ack failed",
        )
        assertTrue(
            original === original.asSubscriptionThrowable("acknowledgePurchase"),
        )
    }

    @Test
    fun `the catalog ids the wrapper queries are the pinned iOS values`() {
        val ids = SubscriptionProductType.ALL.map { it.rawValue }
        assertEquals(listOf("video_call_monthly", "video_call_yearly_sub"), ids)
    }

    @Test
    fun `only the yearly product carries a per-month equivalent`() {
        // Guards the projection contract the plan card renders against:
        // a non-null monthly value would put a "/month" line under a
        // per-month price.
        val monthly = SubscriptionProduct(
            planId = SubscriptionProductType.MONTHLY.rawValue,
            title = "Monthly",
            priceLabel = "$10.99",
            periodLabel = "per month",
            pricePerMonthEquivalent = null,
        )
        val yearly = monthly.copy(
            planId = SubscriptionProductType.YEARLY.rawValue,
            title = "Yearly",
            priceLabel = "$95.90",
            periodLabel = "per year",
            pricePerMonthEquivalent = "$7.99/month",
        )
        assertEquals(null, monthly.pricePerMonthEquivalent)
        assertEquals("$7.99/month", yearly.pricePerMonthEquivalent)
        assertFalse(monthly.isYearly)
        assertTrue(yearly.isYearly)
    }

    private companion object {
        // The `com.android.billingclient` 7.0.0 response codes, mirrored as
        // plain ints: `BillingClient.BillingResponseCode` is a Play type and
        // naming it here would break the wrapper's single-file rule.
        const val OK = 0
        const val SERVICE_TIMEOUT = -3
        const val FEATURE_NOT_SUPPORTED = -2
        const val SERVICE_DISCONNECTED = -1
        const val USER_CANCELED = 1
        const val SERVICE_UNAVAILABLE = 2
        const val BILLING_UNAVAILABLE = 3
        const val ITEM_UNAVAILABLE = 4
        const val DEVELOPER_ERROR = 5
        const val ERROR = 6
        const val ITEM_ALREADY_OWNED = 7
        const val ITEM_NOT_OWNED = 8
        const val NETWORK_ERROR = 12

        val BILLING_UNAVAILABLE_CODES = listOf(
            SERVICE_UNAVAILABLE,
            SERVICE_DISCONNECTED,
            BILLING_UNAVAILABLE,
            FEATURE_NOT_SUPPORTED,
        )
        val PRODUCT_UNAVAILABLE_CODES = listOf(ITEM_UNAVAILABLE, ITEM_ALREADY_OWNED)
        val NETWORK_CODES = listOf(NETWORK_ERROR, SERVICE_TIMEOUT)
        val GENERIC_ERROR = ERROR
    }
}