package com.kornerz.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `User.isUserPremiumNow` is the **single** input to every premium
 * decision on Android — the paywall gate, the Profile row's trailing
 * label — and it mirrors iOS `AppHelper.isUserPremium`
 * (`kornerz/Kornerz/Helper/AppHelper.swift:39-45`), which compares
 * `expiryDate > Date()` and ignores the raw `isUserPremium` bit.
 *
 * The last test is the load-bearing one: iOS's
 * `UserRepository.setUserPremium` (`:170-175`) writes only the three
 * subscription fields and never writes `isUserPremium`, so that bit is
 * permanently `false` in Firestore for every subscriber on both
 * platforms. Gating on it would show the paywall to paying users.
 */
class UserPremiumTest {

    @Test
    fun `a future expiry means premium`() {
        assertTrue(user(expiryDate = "2999-12-01T00:00:00Z").isUserPremiumNow)
    }

    @Test
    fun `a past expiry does not mean premium`() {
        assertFalse(user(expiryDate = "2000-01-01T00:00:00Z").isUserPremiumNow)
    }

    @Test
    fun `a null expiry does not mean premium`() {
        val user = user(expiryDate = null)
        assertFalse(user.isUserPremiumNow)
        assertNull(user.subscriptionExpiryInstant)
    }

    @Test
    fun `a malformed expiry degrades to free rather than throwing`() {
        val user = user(expiryDate = "not-a-date")
        assertNull(user.subscriptionExpiryInstant)
        assertFalse(user.isUserPremiumNow)
    }

    @Test
    fun `the raw isPremium bit is not consulted`() {
        // Exactly the shape iOS leaves in Firestore: the bit says false,
        // the expiry says the user is paying.
        val user = user(expiryDate = "2999-12-01T00:00:00Z", isPremium = false)
        assertFalse(user.isPremium)
        assertTrue(user.isUserPremiumNow)
    }

    @Test
    fun `a true isPremium bit with a past expiry still reads as free`() {
        // The mirror of the test above: the bit cannot make a lapsed
        // subscriber premium either.
        val user = user(expiryDate = "2000-01-01T00:00:00Z", isPremium = true)
        assertTrue(user.isPremium)
        assertFalse(user.isUserPremiumNow)
    }

    @Test
    fun `an iOS-written document reads as premium on Android`() {
        // Cross-platform read: `subscriptionPlatform` is written but never
        // branched on, so an "iOS" subscriber is premium here too.
        val user = user(expiryDate = "2999-12-01T00:00:00Z").copy(
            subscriptionType = SubscriptionProductType.MONTHLY.rawValue,
            subscriptionPlatform = "iOS",
        )
        assertTrue(user.isUserPremiumNow)
        assertEquals(
            SubscriptionProductType.MONTHLY,
            SubscriptionProductType.fromRawValue(user.subscriptionType),
        )
    }

    @Test
    fun `a parseable expiry exposes an Instant`() {
        assertNotNull(user(expiryDate = "2999-12-01T00:00:00Z").subscriptionExpiryInstant)
    }

    @Test
    fun `the derived properties are not persisted`() {
        // They must stay invisible to equals/hashCode and to the DataStore
        // round trip, or a cache write would look like a schema change.
        val a = user(expiryDate = "2999-12-01T00:00:00Z")
        val b = user(expiryDate = "2000-01-01T00:00:00Z")
        assertTrue(a == a.copy())
        assertFalse(a == b)
        assertTrue(a.hashCode() == a.copy().hashCode())
    }

    private fun user(expiryDate: String?, isPremium: Boolean = false) = User(
        id = "u1",
        email = "satoshi@example.com",
        displayName = "Satoshi Black",
        nickname = "satoshi",
        isPremium = isPremium,
        subscriptionExpiryDate = expiryDate,
    )
}