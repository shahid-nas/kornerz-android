package com.kornerz.feature.shell

import com.kornerz.core.model.User
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The paywall gate's five clauses (task 15.5).
 *
 * Each clause is asserted to block presentation **on its own**, because a
 * clause that is only ever exercised alongside the others can silently
 * rot into dead code. The clauses mirror iOS's four-clause guard
 * (`AppTabController.showSubscriptionScreen()`, `:209-229`) plus the
 * Android-only per-process dismissal latch that stands in for iOS's
 * implicit `isSubscriptionOpened()` check on `presentedViewController`.
 *
 * [PlayEntitlementChecker] is the seam `feature/shell` declares so the
 * Play SDK never reaches the shell (`docs/architecture.md` §2.5); faking
 * it here is the whole reason clause 3 is testable on the JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PremiumGateTest {

    private val sessionRepository: SessionRepository = mockk()
    private val entitlementChecker: PlayEntitlementChecker = mockk()

    private val sessionFlow = MutableStateFlow<SessionState>(
        SessionState.Authenticated(freeUser()),
    )

    private fun gate() = PremiumGate(
        sessionRepository = sessionRepository,
        entitlementChecker = entitlementChecker,
    )

    private fun givenSession(user: User?) {
        every { sessionRepository.data } returns sessionFlow
        sessionFlow.value =
            if (user == null) SessionState.Anonymous else SessionState.Authenticated(user)
    }

    private fun givenNoPlayEntitlement() {
        coEvery { entitlementChecker.hasActiveSubscription() } returns false
    }

    private fun givenPlayEntitlement() {
        coEvery { entitlementChecker.hasActiveSubscription() } returns true
    }

    // ---- the all-clear path ---------------------------------------------

    @Test
    fun `an authenticated free user with no entitlement and no latch is presented`() = runTest {
        givenSession(freeUser())
        givenNoPlayEntitlement()
        val gate = gate()

        assertTrue(gate.requestPresent())
        assertTrue(gate.shouldPresent.value)
    }

    // ---- clause by clause -----------------------------------------------

    @Test
    fun `clause 1 - already on the plans route blocks presentation`() = runTest {
        givenSession(freeUser())
        givenNoPlayEntitlement()
        val gate = gate()
        gate.markPlansRouteOpen(true)

        assertFalse(gate.requestPresent())
        assertFalse(gate.shouldPresent.value)
        // The expensive Play round trip is never paid for a paywall that is
        // already on screen.
        coVerify(exactly = 0) { entitlementChecker.hasActiveSubscription() }
    }

    @Test
    fun `clause 2 - an anonymous session blocks presentation`() = runTest {
        givenSession(null)
        givenNoPlayEntitlement()
        val gate = gate()

        assertFalse(gate.requestPresent())
        coVerify(exactly = 0) { entitlementChecker.hasActiveSubscription() }
    }

    @Test
    fun `clause 3 - an active Play entitlement blocks presentation`() = runTest {
        givenSession(freeUser())
        givenPlayEntitlement()
        val gate = gate()

        assertFalse(gate.requestPresent())
    }

    @Test
    fun `clause 4 - a live expiry blocks presentation`() = runTest {
        givenSession(premiumUser())
        givenNoPlayEntitlement()
        val gate = gate()

        assertFalse(
            "a subscriber must never be shown the paywall",
            gate.requestPresent(),
        )
        coVerify(exactly = 0) { entitlementChecker.hasActiveSubscription() }
    }

    @Test
    fun `clause 4 ignores the raw isUserPremium bit`() = runTest {
        // `isUserPremium` is write-only on both platforms: iOS's
        // `UserRepository.setUserPremium` never writes it, so the bit sits
        // at whatever `FirestoreAuthApi.signUp` put there (`false`) and
        // never flips on renewal or cancellation. It is flipped to `true`
        // here precisely to prove the gate never reads it — gating on it
        // would strand a subscriber behind a paywall they cannot leave,
        // because the bit never clears.
        givenSession(freeUser().copy(isPremium = true))
        givenNoPlayEntitlement()
        val gate = gate()

        assertTrue(
            "the stored bit must not satisfy the gate on its own",
            gate.requestPresent(),
        )
    }

    @Test
    fun `clause 4 is false again once the expiry lapses`() = runTest {
        givenSession(
            freeUser().copy(
                subscriptionExpiryDate = "2000-01-01T00:00:00Z",
                subscriptionType = "video_call_monthly",
            ),
        )
        givenNoPlayEntitlement()
        val gate = gate()

        assertTrue(
            "an expired subscriber is a free user again",
            gate.requestPresent(),
        )
    }

    @Test
    fun `clause 5 - the per-process dismissal latch blocks re-presentation`() = runTest {
        givenSession(freeUser())
        givenNoPlayEntitlement()
        val gate = gate()

        assertTrue(gate.requestPresent())

        gate.markDismissed()

        assertFalse(gate.requestPresent())
        assertFalse(gate.shouldPresent.value)
    }

    @Test
    fun `the latch is scoped to one process, not to the account`() = runTest {
        // No `reset()` between dismissals: a second evaluation inside the
        // same process still returns false, which is what stops the
        // paywall re-presenting on every tab switch.
        givenSession(freeUser())
        givenNoPlayEntitlement()
        val gate = gate()

        gate.markDismissed()

        assertFalse(gate.requestPresent())
        assertFalse(gate.requestPresent())
    }

    // ---- the latches that release the gate ------------------------------

    @Test
    fun `markSatisfied clears the dismissal latch`() = runTest {
        givenSession(freeUser())
        givenNoPlayEntitlement()
        val gate = gate()
        gate.markDismissed()

        gate.markSatisfied()

        assertFalse(
            "a purchase mid-session is never re-gated",
            gate.requestPresent(),
        )
    }

    @Test
    fun `markSatisfied itself blocks presentation until reset`() = runTest {
        givenSession(freeUser())
        givenNoPlayEntitlement()
        val gate = gate()

        gate.markSatisfied()

        assertFalse(gate.requestPresent())
        assertFalse(gate.shouldPresent.value)
    }

    @Test
    fun `reset clears both latches so a fresh login re-evaluates`() = runTest {
        givenSession(freeUser())
        givenNoPlayEntitlement()
        val gate = gate()
        gate.markDismissed()

        gate.reset()

        assertTrue(
            "logout-and-login within one process re-runs the iOS post-login gate",
            gate.requestPresent(),
        )
    }

    @Test
    fun `reset also clears the satisfied latch`() = runTest {
        givenSession(freeUser())
        givenNoPlayEntitlement()
        val gate = gate()
        gate.markSatisfied()

        gate.reset()

        assertTrue(gate.requestPresent())
    }

    @Test
    fun `reset clears the already-on-plans-route marker`() = runTest {
        givenSession(freeUser())
        givenNoPlayEntitlement()
        val gate = gate()
        gate.markPlansRouteOpen(true)

        gate.reset()

        assertTrue(gate.requestPresent())
    }

    // ---- helpers ---------------------------------------------------------

    private fun freeUser() = User(
        id = "u1",
        email = "satoshi@kornerz.io",
        displayName = "Satoshi Nakamoto",
        nickname = "satoshi",
    )

    private fun premiumUser() = freeUser().copy(
        subscriptionExpiryDate = Instant.parse(FUTURE_EXPIRY).toString(),
        subscriptionType = "video_call_yearly_sub",
        subscriptionPlatform = "iOS",
    )

    private companion object {
        const val FUTURE_EXPIRY = "2999-01-01T00:00:00Z"
    }
}