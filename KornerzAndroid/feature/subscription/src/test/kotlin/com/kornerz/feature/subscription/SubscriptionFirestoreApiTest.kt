package com.kornerz.feature.subscription

import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.kornerz.feature.subscription.data.network.SubscriptionFirestoreApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * Mocks at the Firestore SDK boundary, which is the codebase's only testing
 * pattern (see `feature/profile`'s `UserProfileRepositoryTest`) — there is no
 * emulator harness and no Firestore fake.
 *
 * `Task`s are stood up as real [Tasks] results rather than relaxed mocks: a
 * relaxed `Task` never completes, so `await()` would suspend forever. That
 * keeps the test honest about the suspend/await path the production code
 * actually takes.
 *
 * The three assertions that matter are all about *which* write is issued:
 * `updateData` rather than `setData` (a `set` would clobber the other ~20
 * fields on the document), exactly three keys, and no write at all when the
 * stored expiry already matches.
 */
class SubscriptionFirestoreApiTest {

    private val firestore: FirebaseFirestore = mockk(relaxed = true)
    private val collection: CollectionReference = mockk(relaxed = true)
    private val document: DocumentReference = mockk(relaxed = true)
    private val snapshot: DocumentSnapshot = mockk(relaxed = true)

    private val api = SubscriptionFirestoreApi(firestore)

    private fun givenDocument(existingExpiry: Date?) {
        every { firestore.collection("Users") } returns collection
        every { collection.document(any()) } returns document
        every { document.get() } returns Tasks.forResult(snapshot)
        every { snapshot.exists() } returns true
        every { snapshot.getTimestamp("subscriptionExpiryDate") } returns
            existingExpiry?.let(::Timestamp)
        every { document.update(any<Map<String, Any>>()) } returns Tasks.forResult(null)
    }

    @Test
    fun `setSubscription writes exactly the three iOS fields`() = runTest {
        givenDocument(existingExpiry = null)

        val result = api.setSubscription(
            email = "a@b.com",
            expiryDate = Instant.parse("2026-12-01T00:00:00Z"),
            planId = "video_call_yearly_sub",
            platform = "Android",
        )

        assertTrue("expected success, got $result", result.isSuccess)
        val payload = slot<Map<String, Any>>()
        coVerify(exactly = 1) { document.update(capture(payload)) }
        assertEquals(
            setOf("subscriptionExpiryDate", "subscriptionType", "subscriptionPlatform"),
            payload.captured.keys,
        )
        assertEquals("video_call_yearly_sub", payload.captured["subscriptionType"])
        assertEquals("Android", payload.captured["subscriptionPlatform"])
        // Written as a Firestore Timestamp so the on-wire shape matches
        // iOS's `Timestamp.dateValue()`.
        assertTrue(payload.captured["subscriptionExpiryDate"] is Timestamp)
        assertEquals(
            Instant.parse("2026-12-01T00:00:00Z").toEpochMilliseconds(),
            (payload.captured["subscriptionExpiryDate"] as Timestamp).toDate().time,
        )
    }

    @Test
    fun `setSubscription targets Users slash email`() = runTest {
        givenDocument(existingExpiry = null)

        api.setSubscription(
            email = "a@b.com",
            expiryDate = Instant.parse("2026-12-01T00:00:00Z"),
            planId = "video_call_monthly",
            platform = "Android",
        )

        // Not `codeUser`, and not the lowercase `users/{uid}` FCM path.
        every { firestore.collection("Users") } returns collection
        coVerify { firestore.collection("Users") }
        coVerify { collection.document("a@b.com") }
    }

    @Test
    fun `setSubscription patches the document instead of replacing it`() = runTest {
        givenDocument(existingExpiry = null)

        api.setSubscription(
            email = "a@b.com",
            expiryDate = Instant.parse("2026-12-01T00:00:00Z"),
            planId = "video_call_monthly",
            platform = "Android",
        )

        // `set` would overwrite every other field on the user document.
        coVerify(exactly = 0) { document.set(any<Map<String, Any>>(), any()) }
        coVerify(exactly = 0) { document.set(any<Map<String, Any>>()) }
    }

    @Test
    fun `setSubscription never writes isUserPremium`() = runTest {
        givenDocument(existingExpiry = null)

        api.setSubscription(
            email = "a@b.com",
            expiryDate = Instant.parse("2026-12-01T00:00:00Z"),
            planId = "video_call_monthly",
            platform = "Android",
        )

        val payload = slot<Map<String, Any>>()
        coVerify { document.update(capture(payload)) }
        assertFalse(
            "iOS's setUserPremium never writes isUserPremium",
            payload.captured.containsKey("isUserPremium"),
        )
    }

    @Test
    fun `an identical stored expiry is a no-op`() = runTest {
        val expiry = Instant.parse("2026-12-01T00:00:00Z")
        givenDocument(existingExpiry = Date(expiry.toEpochMilliseconds()))

        val result = api.setSubscription("a@b.com", expiry, "video_call_yearly_sub", "Android")

        assertTrue("expected success, got $result", result.isSuccess)
        // iOS `UserRepository.swift:168` returns early in this case, so a
        // repeated restore issues no redundant write.
        coVerify(exactly = 0) { document.update(any<Map<String, Any>>()) }
    }

    @Test
    fun `a different stored expiry is written`() = runTest {
        givenDocument(
            existingExpiry = Date(Instant.parse("2026-01-01T00:00:00Z").toEpochMilliseconds()),
        )

        api.setSubscription("a@b.com", Instant.parse("2026-12-01T00:00:00Z"), "video_call_yearly_sub", "Android")

        coVerify(exactly = 1) { document.update(any<Map<String, Any>>()) }
    }

    @Test
    fun `a blank email fails without issuing a write`() = runTest {
        givenDocument(existingExpiry = null)

        val result = api.setSubscription(
            email = "  ",
            expiryDate = Instant.parse("2026-12-01T00:00:00Z"),
            planId = "video_call_monthly",
            platform = "Android",
        )

        assertTrue("expected failure, got $result", result.isFailure)
        coVerify(exactly = 0) { document.update(any<Map<String, Any>>()) }
    }

    @Test
    fun `a read failure is surfaced rather than silently skipping the write`() = runTest {
        every { firestore.collection("Users") } returns collection
        every { collection.document(any()) } returns document
        every { document.get() } returns Tasks.forException(
            IllegalStateException("permission denied"),
        )

        val result = api.setSubscription(
            email = "a@b.com",
            expiryDate = Instant.parse("2026-12-01T00:00:00Z"),
            planId = "video_call_monthly",
            platform = "Android",
        )

        assertTrue("expected failure, got $result", result.isFailure)
        // The grant must not be reported as successful when the no-op guard
        // could not be evaluated.
        coVerify(exactly = 0) { document.update(any<Map<String, Any>>()) }
    }

    @Test
    fun `clearSubscription deletes the same three keys`() = runTest {
        givenDocument(existingExpiry = null)

        val result = api.clearSubscription("a@b.com")

        assertTrue("expected success, got $result", result.isSuccess)
        val payload = slot<Map<String, Any>>()
        coVerify(exactly = 1) { document.update(capture(payload)) }
        assertEquals(
            setOf("subscriptionExpiryDate", "subscriptionType", "subscriptionPlatform"),
            payload.captured.keys,
        )
        assertTrue(
            "each value must be a delete sentinel",
            payload.captured.values.all { it is FieldValue },
        )
    }

    @Test
    fun `clearSubscription on a blank email fails without issuing a write`() = runTest {
        givenDocument(existingExpiry = null)

        val result = api.clearSubscription("")

        assertTrue("expected failure, got $result", result.isFailure)
        coVerify(exactly = 0) { document.update(any<Map<String, Any>>()) }
    }
}