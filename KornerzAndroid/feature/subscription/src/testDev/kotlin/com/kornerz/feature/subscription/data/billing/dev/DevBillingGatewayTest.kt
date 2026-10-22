package com.kornerz.feature.subscription.data.billing.dev

import app.cash.turbine.test
import com.kornerz.core.model.SubscriptionProductType
import com.kornerz.feature.subscription.data.SubscriptionError
import com.kornerz.feature.subscription.data.SubscriptionException
import com.kornerz.feature.subscription.data.billing.BillingConnectionState
import com.kornerz.feature.subscription.data.billing.BillingPurchaseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Behavioural tests for the `dev` flavor's simulated billing gateway.
 *
 * Lives in `src/testDev/kotlin` so it compiles **only** into the `dev`
 * variant — the same guarantee that keeps `DevBillingGateway` itself out of
 * a release build. The `prod` unit-test task does not see this file at all.
 *
 * The fake is production-shaped code that QA and PM will click through on
 * every `assembleDevDebug` build until Play is live, so it is held to the
 * same standard as the real wrapper: the pinned iOS product ids, the
 * monthly/yearly distinction, and the async-then-acknowledge purchase
 * sequence all have to hold, or the demo stops reflecting the real flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DevBillingGatewayTest {

    /**
     * Set before the gateway is constructed: its internal scope captures
     * `Dispatchers.Main.immediate` at construction, so the purchase delay
     * becomes virtual time rather than a real 900 ms sleep.
     */
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- catalog ---------------------------------------------------------

    @Test
    fun `the catalog is the two pinned iOS product ids in order`() = runTest {
        val products = gateway().queryProductDetails(
            SubscriptionProductType.ALL.map { it.rawValue },
        ).getOrThrow()

        assertEquals(
            listOf("video_call_monthly", "video_call_yearly_sub"),
            products.map { it.planId },
        )
    }

    @Test
    fun `an unrequested product id is not in the catalog`() = runTest {
        val products = gateway().queryProductDetails(listOf("some_other_product"))
            .getOrThrow()

        assertTrue(products.isEmpty())
    }

    @Test
    fun `only the yearly plan carries a per-month equivalent`() = runTest {
        val products = gateway().queryProductDetails(
            SubscriptionProductType.ALL.map { it.rawValue },
        ).getOrThrow()

        val monthly = products.first { it.productType == SubscriptionProductType.MONTHLY }
        val yearly = products.first { it.isYearly }

        assertNull(
            "a non-null monthly value would put a /month line under a per-month price",
            monthly.pricePerMonthEquivalent,
        )
        assertEquals("$7.99/month", yearly.pricePerMonthEquivalent)
    }

    @Test
    fun `both plans report a free trial, matching the iOS storekit config`() = runTest {
        val products = gateway().queryProductDetails(
            SubscriptionProductType.ALL.map { it.rawValue },
        ).getOrThrow()

        // `Kornerz-SKConfig.storekit:185-200` gives both products a P1W
        // introductory offer, so the dev build mirrors that rather than
        // inventing a different shape.
        assertTrue(products.all { it.hasFreeTrialOffer })
    }

    @Test
    fun `every plan carries an offer token so the trial label can drive`() = runTest {
        val products = gateway().queryProductDetails(
            SubscriptionProductType.ALL.map { it.rawValue },
        ).getOrThrow()

        assertTrue(products.all { !it.offerTag.isNullOrBlank() })
    }

    // ---- purchase --------------------------------------------------------

    @Test
    fun `the purchase outcome arrives asynchronously, not synchronously`() = runTest {
        val gateway = gateway()

        gateway.purchaseOutcomes.test {
            gateway.launchPurchase(SubscriptionProductType.MONTHLY.rawValue, "offer")
                .getOrThrow()

            // Nothing yet: the ViewModel only drains outcomes while
            // `isPurchasing` is true, so a synchronous emission would skip
            // the acknowledge-then-grant sequence entirely.
            expectNoEvents()

            advanceTimeBy(PURCHASE_SETTLE_MILLIS)

            val outcome = awaitItem()
            assertTrue("expected Purchased, got $outcome", outcome is BillingPurchaseResult.Purchased)
            val purchased = outcome as BillingPurchaseResult.Purchased
            assertEquals("video_call_monthly", purchased.productId)
            assertTrue("the token must be stable for acknowledgement", purchased.token.isNotBlank())
            assertTrue(purchased.purchaseTimeMillis > 0L)

            expectNoEvents()
        }
    }

    @Test
    fun `an unknown product id is refused with a typed error`() {
        val failure = gateway().launchPurchase("not_a_product", null).exceptionOrNull()

        assertTrue("expected SubscriptionException, got $failure", failure is SubscriptionException)
        assertEquals(
            SubscriptionError.ProductUnavailable,
            (failure as SubscriptionException).errorKind,
        )
    }

    @Test
    fun `acknowledging marks the purchase so restore can re-derive it`() = runTest {
        val gateway = gateway()
        gateway.purchaseOutcomes.test {
            gateway.launchPurchase(SubscriptionProductType.YEARLY.rawValue, null).getOrThrow()
            advanceTimeBy(PURCHASE_SETTLE_MILLIS)
            awaitItem()

            gateway.acknowledgePurchase("dev-token-video_call_yearly_sub").getOrThrow()

            val restored = gateway.restore().getOrThrow()
            assertEquals(1, restored.size)
            assertEquals("video_call_yearly_sub", restored.single().productId)
            assertTrue(restored.single().isAcknowledged)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `restore on a fresh install finds nothing rather than inventing a purchase`() = runTest {
        val records = gateway().restore().getOrThrow()

        assertTrue(records.isEmpty())
    }

    @Test
    fun `endConnection is idempotent and reports Disconnected`() = runTest {
        val gateway = gateway()

        gateway.endConnection()
        gateway.endConnection()

        assertEquals(
            BillingConnectionState.Disconnected,
            gateway.connectionState.value,
        )
    }

    @Test
    fun `the connection reports Ready because there is no license check to fail`() = runTest {
        val state = gateway().connectionState.value

        assertTrue("expected Ready, got $state", state is BillingConnectionState.Ready)
        assertFalse(
            "the dev build must never render the billing-unavailable notice",
            state is BillingConnectionState.Unavailable,
        )
    }

    private fun gateway() = DevBillingGateway()

    private companion object {
        /** Mirrors `DevBillingGateway.PURCHASE_SETTLE_DELAY_MILLIS`. */
        const val PURCHASE_SETTLE_MILLIS = 900L
    }
}