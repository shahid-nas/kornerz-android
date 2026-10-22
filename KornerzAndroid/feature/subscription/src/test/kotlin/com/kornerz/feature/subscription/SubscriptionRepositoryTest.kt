package com.kornerz.feature.subscription

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.SubscriptionProduct
import com.kornerz.core.model.SubscriptionProductType
import com.kornerz.core.model.User
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.feature.subscription.data.ENTITLEMENT_PLATFORM
import com.kornerz.feature.subscription.data.SubscriptionError
import com.kornerz.feature.subscription.data.SubscriptionException
import com.kornerz.feature.subscription.data.SubscriptionRepository
import com.kornerz.feature.subscription.data.billing.SubscriptionBillingGateway
import com.kornerz.feature.subscription.data.billing.PurchaseRecord
import com.kornerz.feature.subscription.data.network.SubscriptionCloudFunctionsApi
import com.kornerz.feature.subscription.data.network.SubscriptionFirestoreApi
import com.kornerz.feature.subscription.data.network.SubscriptionVerificationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repository's orchestration contract: resolve the catalog, grant the
 * entitlement then refresh the cache, fail cleanly while anonymous, and
 * reconcile non-destructively.
 *
 * The collaborators are mocked at their public surface — which is possible
 * precisely because the billing seam keeps every Play type inside
 * `BillingClientApi`. A repository that exposed `ProductDetails` could
 * not be tested here, on either flavor.
 */
class SubscriptionRepositoryTest {

    private val billingGateway: SubscriptionBillingGateway = mockk(relaxed = true)
    private val firestoreApi: SubscriptionFirestoreApi = mockk(relaxed = true)
    private val cloudFunctionsApi: SubscriptionCloudFunctionsApi = mockk(relaxed = true)
    private val sessionRepository: SessionRepository = mockk(relaxed = true)

    private val sessionFlow = MutableStateFlow<SessionState>(SessionState.Anonymous)

    private fun repository() = SubscriptionRepository(
        billingGateway = billingGateway,
        firestoreApi = firestoreApi,
        cloudFunctionsApi = cloudFunctionsApi,
        sessionRepository = sessionRepository,
    )

    private fun givenSession(user: User?) {
        every { sessionRepository.data } returns sessionFlow
        sessionFlow.value = if (user == null) {
            SessionState.Anonymous
        } else {
            SessionState.Authenticated(user)
        }
    }

    private fun user(
        email: String = "a@b.com",
        expiryDate: String? = null,
    ) = User(
        id = "u1",
        email = email,
        displayName = "Satoshi Black",
        nickname = "satoshi",
        subscriptionExpiryDate = expiryDate,
    )

    // ---- catalog --------------------------------------------------------

    @Test
    fun `observeCatalog wraps a resolved catalog in Success`() = runTest {
        coEvery { billingGateway.queryProductDetails(any()) } returns Result.success(
            listOf(monthly(), yearly()),
        )

        val catalog = repository().observeCatalog()

        assertTrue(catalog is AsyncValue.Success)
        assertEquals(2, (catalog as AsyncValue.Success).data.size)
    }

    @Test
    fun `an unregistered application id surfaces Error rather than throwing`() = runTest {
        // The dev flavor's `com.kornerz.kornerz.dev` id is not a Play
        // listing, so this is the failure the plans screen must render a
        // non-fatal notice for.
        coEvery { billingGateway.queryProductDetails(any()) } returns Result.failure(
            SubscriptionException(
                SubscriptionError.BillingUnavailable,
                "queryProductDetails failed",
            ),
        )

        val catalog = repository().observeCatalog()

        assertTrue("expected Error, got $catalog", catalog is AsyncValue.Error)
    }

    @Test
    fun `an empty catalog is reported as an empty Success, not an exception`() = runTest {
        coEvery { billingGateway.queryProductDetails(any()) } returns Result.success(emptyList())

        val catalog = repository().observeCatalog()

        assertTrue(catalog is AsyncValue.Success)
        assertTrue((catalog as AsyncValue.Success).data.isEmpty())
    }

    @Test
    fun `observeCatalog queries the pinned iOS product ids`() = runTest {
        val ids = slot<List<String>>()
        coEvery { billingGateway.queryProductDetails(capture(ids)) } returns Result.success(emptyList())

        repository().observeCatalog()

        assertEquals(listOf("video_call_monthly", "video_call_yearly_sub"), ids.captured)
    }

    // ---- grant / revoke -------------------------------------------------

    @Test
    fun `grantEntitlement writes the variant's platform then refreshes the cache`() = runTest {
        givenSession(user())
        coEvery { firestoreApi.setSubscription(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { firestoreApi.fetchUser(any()) } returns user(expiryDate = "2999-01-01T00:00:00Z")
        val transform = slot<(User) -> User>()
        coEvery { sessionRepository.updateUser(capture(transform)) } returns Result.success(Unit)

        val result = repository().grantEntitlement(
            email = "a@b.com",
            plan = SubscriptionProductType.YEARLY,
            expiry = Instant.parse("2999-01-01T00:00:00Z"),
        )

        assertTrue("expected success, got $result", result.isSuccess)
        coVerify {
            firestoreApi.setSubscription(
                email = "a@b.com",
                expiryDate = Instant.parse("2999-01-01T00:00:00Z"),
                planId = "video_call_yearly_sub",
                // Flavor-resolved: `"Android"` on prod, `"AndroidDev"` on the
                // simulated build, so a fake entitlement stays greppable in
                // the shared backend.
                platform = ENTITLEMENT_PLATFORM,
            )
        }
        // The cached session user is replaced from the authoritative
        // document, not patched, so a stale subscription field cannot
        // survive the refresh.
        coVerify { sessionRepository.updateUser(any()) }
    }

    @Test
    fun `grantEntitlement does not refresh the cache when the write fails`() = runTest {
        givenSession(user())
        coEvery {
            firestoreApi.setSubscription(any(), any(), any(), any())
        } returns Result.failure(IllegalStateException("offline"))

        val result = repository().grantEntitlement(
            email = "a@b.com",
            plan = SubscriptionProductType.MONTHLY,
            expiry = Instant.parse("2999-01-01T00:00:00Z"),
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { sessionRepository.updateUser(any()) }
    }

    @Test
    fun `an anonymous session fails cleanly without issuing a Firestore write`() = runTest {
        givenSession(null)

        val result = repository().grantEntitlement(
            email = "a@b.com",
            plan = SubscriptionProductType.MONTHLY,
            expiry = Instant.parse("2999-01-01T00:00:00Z"),
        )

        assertTrue("expected failure, got $result", result.isFailure)
        coVerify(exactly = 0) { firestoreApi.setSubscription(any(), any(), any(), any()) }
        coVerify(exactly = 0) { sessionRepository.updateUser(any()) }
    }

    @Test
    fun `revokeEntitlement clears the fields and refreshes`() = runTest {
        givenSession(user(expiryDate = "2999-01-01T00:00:00Z"))
        coEvery { firestoreApi.clearSubscription(any()) } returns Result.success(Unit)
        coEvery { firestoreApi.fetchUser(any()) } returns user()
        coEvery { sessionRepository.updateUser(any()) } returns Result.success(Unit)

        val result = repository().revokeEntitlement("a@b.com")

        assertTrue("expected success, got $result", result.isSuccess)
        coVerify { firestoreApi.clearSubscription("a@b.com") }
        coVerify { sessionRepository.updateUser(any()) }
    }

    @Test
    fun `refreshCachedUser fails when the authoritative document is missing`() = runTest {
        givenSession(user())
        coEvery { firestoreApi.fetchUser(any()) } returns null

        val result = repository().refreshCachedUser("a@b.com")

        assertTrue("expected failure, got $result", result.isFailure)
        coVerify(exactly = 0) { sessionRepository.updateUser(any()) }
    }

    // ---- reconciliation -------------------------------------------------

    @Test
    fun `a free user with no local purchase issues no network call`() = runTest {
        givenSession(user(expiryDate = null))
        coEvery { billingGateway.queryPurchases() } returns Result.success(emptyList())

        val result = repository().reconcileEntitlement()

        assertTrue("expected success, got $result", result.isSuccess)
        coVerify(exactly = 0) { cloudFunctionsApi.verifyPurchase(any(), any()) }
        coVerify(exactly = 0) { firestoreApi.clearSubscription(any()) }
    }

    @Test
    fun `a revoked entitlement is cleared on launch`() = runTest {
        givenSession(user(expiryDate = "2999-01-01T00:00:00Z"))
        coEvery { billingGateway.queryPurchases() } returns Result.success(
            listOf(record("token-1", Instant.parse("2999-01-01T00:00:00Z"))),
        )
        coEvery { cloudFunctionsApi.verifyPurchase(any(), any()) } returns Result.success(
            SubscriptionVerificationResult(isActive = false),
        )
        coEvery { firestoreApi.clearSubscription(any()) } returns Result.success(Unit)
        coEvery { firestoreApi.fetchUser(any()) } returns user()
        coEvery { sessionRepository.updateUser(any()) } returns Result.success(Unit)

        val result = repository().reconcileEntitlement()

        assertTrue("expected success, got $result", result.isSuccess)
        coVerify { cloudFunctionsApi.verifyPurchase("token-1", "video_call_monthly") }
        coVerify { firestoreApi.clearSubscription("a@b.com") }
    }

    @Test
    fun `a verification failure leaves local state untouched`() = runTest {
        givenSession(user(expiryDate = "2999-01-01T00:00:00Z"))
        coEvery { billingGateway.queryPurchases() } returns Result.success(
            listOf(record("token-1", Instant.parse("2999-01-01T00:00:00Z"))),
        )
        coEvery { cloudFunctionsApi.verifyPurchase(any(), any()) } returns
            Result.failure(IllegalStateException("function not deployed"))

        val result = repository().reconcileEntitlement()

        // The gate still enforces the local expiry, so losing the backend
        // must not revoke a paying subscriber's premium state.
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { firestoreApi.clearSubscription(any()) }
        coVerify(exactly = 0) { sessionRepository.updateUser(any()) }
    }

    @Test
    fun `an unreachable Play service leaves local state untouched`() = runTest {
        givenSession(user(expiryDate = "2999-01-01T00:00:00Z"))
        coEvery { billingGateway.queryPurchases() } returns Result.failure(
            SubscriptionException(SubscriptionError.BillingUnavailable, "unavailable"),
        )

        val result = repository().reconcileEntitlement()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { firestoreApi.clearSubscription(any()) }
    }

    @Test
    fun `the most recent purchase token is the one verified`() = runTest {
        givenSession(user(expiryDate = "2999-01-01T00:00:00Z"))
        coEvery { billingGateway.queryPurchases() } returns Result.success(
            listOf(
                record("older", Instant.parse("2998-01-01T00:00:00Z")),
                record("newer", Instant.parse("2999-06-01T00:00:00Z")),
            ),
        )
        coEvery { cloudFunctionsApi.verifyPurchase(any(), any()) } returns Result.success(
            SubscriptionVerificationResult(isActive = false),
        )
        coEvery { firestoreApi.clearSubscription(any()) } returns Result.success(Unit)
        coEvery { firestoreApi.fetchUser(any()) } returns user()
        coEvery { sessionRepository.updateUser(any()) } returns Result.success(Unit)

        repository().reconcileEntitlement()

        coVerify { cloudFunctionsApi.verifyPurchase("newer", "video_call_monthly") }
    }

    @Test
    fun `locally premium with no Play purchase leaves the local expiry authoritative`() = runTest {
        givenSession(user(expiryDate = "2999-01-01T00:00:00Z"))
        coEvery { billingGateway.queryPurchases() } returns Result.success(emptyList())

        val result = repository().reconcileEntitlement()

        assertTrue("expected success, got $result", result.isSuccess)
        // No token to verify, and a Play-reported empty set is not proof of
        // revocation — so nothing is cleared.
        coVerify(exactly = 0) { cloudFunctionsApi.verifyPurchase(any(), any()) }
        coVerify(exactly = 0) { firestoreApi.clearSubscription(any()) }
    }

    // ---- restore / acknowledge ------------------------------------------

    @Test
    fun `restore delegates to the wrapper without charging the user`() = runTest {
        coEvery { billingGateway.restore() } returns Result.success(
            listOf(record("token-1", Instant.parse("2999-01-01T00:00:00Z"))),
        )

        val result = repository().restore()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        coVerify(exactly = 1) { billingGateway.restore() }
    }

    @Test
    fun `acknowledge surfaces a typed NotAcknowledged failure`() = runTest {
        coEvery { billingGateway.acknowledgePurchase(any()) } returns Result.failure(
            SubscriptionException(SubscriptionError.NotAcknowledged, "ack failed"),
        )

        val result = repository().acknowledge("token-1")

        val error = result.exceptionOrNull()
        assertTrue(error is SubscriptionException)
        assertEquals(
            SubscriptionError.NotAcknowledged,
            (error as SubscriptionException).errorKind,
        )
    }

    // ---- helpers --------------------------------------------------------

    private fun monthly() = SubscriptionProduct(
        planId = SubscriptionProductType.MONTHLY.rawValue,
        title = "Monthly",
        priceLabel = "$10.99",
        periodLabel = "per month",
    )

    private fun yearly() = SubscriptionProduct(
        planId = SubscriptionProductType.YEARLY.rawValue,
        title = "Yearly",
        priceLabel = "$95.90",
        periodLabel = "per year",
        pricePerMonthEquivalent = "$7.99/month",
    )

    private fun record(token: String, purchasedAt: Instant) = PurchaseRecord(
        token = token,
        productId = SubscriptionProductType.MONTHLY.rawValue,
        purchaseTimeMillis = purchasedAt.toEpochMilliseconds(),
        isAcknowledged = true,
    )
}
