package com.kornerz.feature.subscription

import app.cash.turbine.test
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.SubscriptionProduct
import com.kornerz.core.model.SubscriptionProductType
import com.kornerz.core.model.User
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.feature.subscription.data.SubscriptionError
import com.kornerz.feature.subscription.data.SubscriptionException
import com.kornerz.feature.subscription.data.SubscriptionRepository
import com.kornerz.feature.subscription.data.billing.SubscriptionBillingGateway
import com.kornerz.feature.subscription.data.billing.BillingConnectionState
import com.kornerz.feature.subscription.data.billing.BillingPurchaseResult
import com.kornerz.feature.subscription.data.billing.BillingUnavailableReason
import com.kornerz.feature.subscription.data.billing.PurchaseRecord
import com.kornerz.feature.subscription.data.deriveExpiryFrom
import com.kornerz.feature.subscription.plans.SubscriptionNavEvent
import com.kornerz.feature.subscription.plans.SubscriptionViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The plans screen's UDF behaviour (task 15.4).
 *
 * Everything is faked at the two boundaries the architecture guarantees:
 * [SubscriptionRepository] and [SubscriptionBillingGateway], both of
 * which expose only Compose-free data. That is what makes this runnable on
 * the JVM with no device, no Play Store, and no Robolectric — a ViewModel
 * that held a `BillingClient` or a `ProductDetails` could not be tested here.
 *
 * No `com.android.billingclient` import appears in this file, either: the
 * wrapper's `PurchasesUpdatedListener` delivers outcomes on a `Flow` of
 * plain data, and the purchase flow is driven from that end rather than
 * from a fabricated Play `BillingResult`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionViewModelTest {

    private val subscriptionRepository: SubscriptionRepository = mockk(relaxed = true)
    private val billingGateway: SubscriptionBillingGateway = mockk(relaxed = true)
    private val sessionRepository: SessionRepository = mockk(relaxed = true)

    private val sessionFlow = MutableStateFlow<SessionState>(
        SessionState.Authenticated(freeUser()),
    )
    private val connectionFlow =
        MutableStateFlow<BillingConnectionState>(BillingConnectionState.Ready(PLAY_OK))
    private val outcomes = Channel<BillingPurchaseResult>(Channel.UNLIMITED)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { sessionRepository.data } returns sessionFlow
        every { billingGateway.connectionState } returns connectionFlow
        every { billingGateway.purchaseOutcomes } returns outcomes.receiveAsFlow()
        // Stated explicitly rather than left to a relaxed mock: `Result` is
        // an inline value class, and the tap's behaviour depends on the
        // sheet having been accepted.
        every { billingGateway.launchPurchase(any(), any()) } returns Result.success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SubscriptionViewModel(
        subscriptionRepository = subscriptionRepository,
        billingGateway = billingGateway,
        sessionRepository = sessionRepository,
    )

    private fun givenCatalog(
        products: List<SubscriptionProduct> = listOf(monthly(), yearly()),
    ) {
        coEvery { subscriptionRepository.observeCatalog() } returns AsyncValue.Success(products)
    }

    // ---- catalog resolution ---------------------------------------------

    @Test
    fun `init resolves the catalog`() = runTest {
        givenCatalog()

        val vm = viewModel()

        coVerify(exactly = 1) { subscriptionRepository.observeCatalog() }
        val products = vm.state.value.products
        assertTrue("expected Success, got $products", products is AsyncValue.Success)
        assertEquals(2, (products as AsyncValue.Success).data.size)
    }

    @Test
    fun `an empty catalog becomes an error state rather than a blank screen`() = runTest {
        givenCatalog(emptyList())

        val vm = viewModel()

        val products = vm.state.value.products
        assertTrue(
            "an unpublished catalog needs a retry, not an empty list",
            products is AsyncValue.Error,
        )
    }

    @Test
    fun `the selection defaults to MONTHLY`() = runTest {
        givenCatalog()

        assertEquals(SubscriptionProductType.MONTHLY, viewModel().state.value.selected)
    }

    // ---- trial eligibility follows the selected card -------------------

    @Test
    fun `trial eligibility follows the selected plan`() = runTest {
        givenCatalog(
            listOf(
                monthly(hasFreeTrialOffer = true),
                yearly(hasFreeTrialOffer = false),
            ),
        )

        val vm = viewModel()
        assertTrue(
            "the monthly base plan carries the Play free-trial offer",
            vm.state.value.isTrialEligible,
        )

        vm.onPlanSelected(SubscriptionProductType.YEARLY)

        assertFalse(
            "the yearly plan has no trial, so the label must flip to Subscribe",
            vm.state.value.isTrialEligible,
        )

        vm.onPlanSelected(SubscriptionProductType.MONTHLY)

        assertTrue(vm.state.value.isTrialEligible)
    }

    @Test
    fun `selecting the yearly plan is the only way to reach the per-month line`() = runTest {
        givenCatalog()

        val vm = viewModel()

        assertNull(
            "the monthly card renders one price line",
            vm.state.value.selectedProduct?.pricePerMonthEquivalent,
        )

        vm.onPlanSelected(SubscriptionProductType.YEARLY)

        assertEquals(YEARLY_PRICE, vm.state.value.selectedPriceLabel)
        assertEquals(
            YEARLY_MONTHLY_EQUIVALENT_PER_MONTH,
            vm.state.value.selectedProduct?.pricePerMonthEquivalent,
        )
    }

    // ---- billing-unavailable notice (the dev flavor) --------------------

    @Test
    fun `an unregistered application id renders a non-fatal notice`() = runTest {
        givenCatalog()
        val vm = viewModel()
        assertNull(vm.state.value.billingNotice)

        // `com.kornerz.kornerz.dev` is not a Play listing, so the dev build
        // lands here. The screen must still render its chrome.
        connectionFlow.value = BillingConnectionState.Unavailable(
            BillingUnavailableReason.UnsupportedApplicationId,
        )

        assertNotNull("expected a notice, got ${vm.state.value.billingNotice}", vm.state.value.billingNotice)
        assertTrue(
            "the catalog survives an unavailable connection",
            vm.state.value.products is AsyncValue.Success,
        )
        assertNull("an unavailable connection is not a terminal error", vm.state.value.error)
    }

    // ---- the subscribe flow ---------------------------------------------

    @Test
    fun `a purchase acknowledges then grants then reports success`() = runTest {
        givenCatalog()
        coEvery { subscriptionRepository.acknowledge(any()) } returns Result.success(Unit)
        coEvery { subscriptionRepository.grantEntitlement(any(), any(), any()) } returns
            Result.success(Unit)
        val vm = viewModel()

        vm.navEvents.test {
            vm.onSubscribeTap()
            assertTrue("the tap is in flight awaiting the sheet", vm.state.value.isPurchasing)

            outcomes.send(
                BillingPurchaseResult.Purchased(
                    token = TOKEN,
                    productId = SubscriptionProductType.MONTHLY.rawValue,
                    purchaseTimeMillis = PURCHASE_TIME,
                ),
            )

            assertEquals(
                SubscriptionNavEvent.PurchaseSucceeded(SubscriptionProductType.MONTHLY),
                awaitItem(),
            )
            expectNoEvents()
        }

        assertFalse(vm.state.value.isPurchasing)
        assertNull(vm.state.value.error)
        coVerify {
            subscriptionRepository.grantEntitlement(
                email = EMAIL,
                plan = SubscriptionProductType.MONTHLY,
                expiry = SubscriptionProductType.MONTHLY.deriveExpiryFrom(PURCHASE_TIME),
            )
        }
    }

    @Test
    fun `a failed acknowledgement blocks the success navigation`() = runTest {
        givenCatalog()
        coEvery { subscriptionRepository.acknowledge(any()) } returns Result.failure(
            SubscriptionException(SubscriptionError.NotAcknowledged, "ack failed"),
        )
        val vm = viewModel()

        vm.navEvents.test {
            vm.onSubscribeTap()
            outcomes.send(
                BillingPurchaseResult.Purchased(
                    token = TOKEN,
                    productId = SubscriptionProductType.MONTHLY.rawValue,
                    purchaseTimeMillis = PURCHASE_TIME,
                ),
            )

            val event = awaitItem()
            assertTrue(
                "expected PurchaseFailed, got $event",
                event is SubscriptionNavEvent.PurchaseFailed,
            )
            expectNoEvents()
        }

        // Google refunds anything left unacknowledged after three days, so
        // the entitlement must not be written at all.
        coVerify(exactly = 0) { subscriptionRepository.grantEntitlement(any(), any(), any()) }
        assertFalse(vm.state.value.isPurchasing)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun `a cancelled sheet clears the in-flight flag without an error`() = runTest {
        givenCatalog()
        val vm = viewModel()

        vm.navEvents.test {
            vm.onSubscribeTap()
            outcomes.send(
                BillingPurchaseResult.Failed(SubscriptionError.UserCancelled),
            )

            expectNoEvents()
        }

        assertFalse("the button must become tappable again", vm.state.value.isPurchasing)
        assertNull(
            "closing the Play sheet is a normal return path, not an error",
            vm.state.value.error,
        )
    }

    @Test
    fun `a pending purchase clears the in-flight flag and reports the wait`() = runTest {
        givenCatalog()
        val vm = viewModel()

        vm.navEvents.test {
            vm.onSubscribeTap()
            outcomes.send(BillingPurchaseResult.Pending)

            val event = awaitItem()
            assertTrue(
                "expected PurchaseFailed, got $event",
                event is SubscriptionNavEvent.PurchaseFailed,
            )
            expectNoEvents()
        }

        assertFalse(vm.state.value.isPurchasing)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun `an anonymous session cannot grant the entitlement`() = runTest {
        givenCatalog()
        coEvery { subscriptionRepository.acknowledge(any()) } returns Result.success(Unit)
        sessionFlow.value = SessionState.Anonymous
        val vm = viewModel()

        vm.navEvents.test {
            vm.onSubscribeTap()
            outcomes.send(
                BillingPurchaseResult.Purchased(
                    token = TOKEN,
                    productId = SubscriptionProductType.MONTHLY.rawValue,
                    purchaseTimeMillis = PURCHASE_TIME,
                ),
            )

            val event = awaitItem()
            assertTrue(
                "expected PurchaseFailed, got $event",
                event is SubscriptionNavEvent.PurchaseFailed,
            )
            expectNoEvents()
        }

        coVerify(exactly = 0) { subscriptionRepository.grantEntitlement(any(), any(), any()) }
        assertFalse(vm.state.value.isPurchasing)
    }

    @Test
    fun `leaving the screen mid-purchase clears the in-flight flag`() = runTest {
        givenCatalog()
        val vm = viewModel()

        vm.onSubscribeTap()
        assertTrue(vm.state.value.isPurchasing)

        vm.onScreenDisposed()

        assertFalse(vm.state.value.isPurchasing)
    }

    // ---- restore ---------------------------------------------------------

    @Test
    fun `restore grants the entitlement and reports the plan`() = runTest {
        givenCatalog()
        coEvery { subscriptionRepository.restore() } returns Result.success(
            listOf(
                PurchaseRecord(
                    token = TOKEN,
                    productId = SubscriptionProductType.YEARLY.rawValue,
                    purchaseTimeMillis = PURCHASE_TIME,
                    isAcknowledged = true,
                ),
            ),
        )
        coEvery { subscriptionRepository.grantEntitlement(any(), any(), any()) } returns
            Result.success(Unit)
        val vm = viewModel()

        vm.navEvents.test {
            vm.onRestoreTap()

            assertEquals(
                SubscriptionNavEvent.Restored(SubscriptionProductType.YEARLY),
                awaitItem(),
            )
            expectNoEvents()
        }

        assertFalse(vm.state.value.isRestoring)
        coVerify(exactly = 1) { subscriptionRepository.restore() }
    }

    @Test
    fun `restore with nothing to recover reports calmly and clears its flag`() = runTest {
        givenCatalog()
        coEvery { subscriptionRepository.restore() } returns Result.success(emptyList())
        val vm = viewModel()

        vm.navEvents.test {
            vm.onRestoreTap()

            // A calm "nothing to restore" report, not a terminal error state.
            val event = awaitItem()
            assertTrue(
                "expected PurchaseFailed, got $event",
                event is SubscriptionNavEvent.PurchaseFailed,
            )
            expectNoEvents()
        }

        assertFalse(vm.state.value.isRestoring)
        assertNull(vm.state.value.error)
        coVerify(exactly = 0) { subscriptionRepository.grantEntitlement(any(), any(), any()) }
    }

    // ---- one-shot navigation events -------------------------------------

    @Test
    fun `the link row emits its own one-shot events`() = runTest {
        givenCatalog()
        val vm = viewModel()

        vm.navEvents.test {
            vm.onPrivacyPolicyTap()
            assertEquals(SubscriptionNavEvent.OpenPrivacyPolicy, awaitItem())

            vm.onTermsOfUseTap()
            assertEquals(SubscriptionNavEvent.OpenTermsOfUse, awaitItem())

            expectNoEvents()
        }
    }

    @Test
    fun `the welcome line falls back to Klouder`() = runTest {
        givenCatalog()
        val vm = viewModel()
        assertEquals("satoshi", vm.nickname.value)

        sessionFlow.value = SessionState.Anonymous

        assertEquals(KLOUDER, vm.nickname.value)
    }

    // ---- helpers ---------------------------------------------------------

    private fun freeUser() = User(
        id = "u1",
        email = EMAIL,
        displayName = "Satoshi Nakamoto",
        nickname = "satoshi",
    )

    private fun monthly(hasFreeTrialOffer: Boolean = true) = SubscriptionProduct(
        planId = SubscriptionProductType.MONTHLY.rawValue,
        title = "Monthly",
        priceLabel = MONTHLY_PRICE,
        periodLabel = "per month",
        pricePerMonthEquivalent = null,
        hasFreeTrialOffer = hasFreeTrialOffer,
        offerTag = if (hasFreeTrialOffer) "trial-tag" else null,
    )

    private fun yearly(hasFreeTrialOffer: Boolean = false) = SubscriptionProduct(
        planId = SubscriptionProductType.YEARLY.rawValue,
        title = "Yearly",
        priceLabel = YEARLY_PRICE,
        periodLabel = "per year",
        pricePerMonthEquivalent = YEARLY_MONTHLY_EQUIVALENT_PER_MONTH,
        hasFreeTrialOffer = hasFreeTrialOffer,
        offerTag = null,
    )

    private companion object {
        /** `BillingClient.BillingResponseCode.OK`, mirrored as a plain int. */
        const val PLAY_OK = 0
        const val EMAIL = "satoshi@kornerz.io"
        const val TOKEN = "purchase-token-1"
        const val PURCHASE_TIME = 1_700_000_000_000L
        const val MONTHLY_PRICE = "$10.99"
        const val YEARLY_PRICE = "$95.90"
        const val YEARLY_MONTHLY_EQUIVALENT_PER_MONTH = "$7.99/month"

        /** iOS `AppHelper.shared.user?.nickName ?? "Klouder"`. */
        const val KLOUDER = "Klouder"
    }
}