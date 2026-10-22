package com.kornerz.feature.profile.data.network

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.kornerz.core.model.SubscriptionProductType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * The P8 read-path fix (task 14.1) and its round-trip guarantee
 * (tasks 14.3 / 15.6).
 *
 * Before this change `UserFirestoreApi.toUserOrNull()` dropped all four
 * premium fields, so **every** Profile read produced a user that looked
 * permanently free — which made the Profile row's trailing label wrong
 * and the paywall gate re-present for a paying subscriber whose
 * `subscriptionExpiryDate` was months in the future. These tests pin the
 * four fields to the decode and pin the two things the decode must *not*
 * get wrong:
 *
 *  - a document written by the **iOS** app (`subscriptionPlatform = "iOS"`,
 *    `subscriptionType = "video_call_monthly"`) must decode to a premium
 *    user, which is the cross-platform guarantee in the
 *    `premium-entitlement` spec;
 *  - the raw `isUserPremium` bit must **not** be the premium decision
 *    input, so `isUserPremiumNow` stays the single authority.
 *
 * No Firestore fake and no emulator: `DocumentSnapshot` and the
 * `Task<DocumentSnapshot>` are mocked at the SDK boundary, which is the
 * same approach `feature/community`'s Firestore test uses.
 */
class UserFirestoreApiPremiumMappingTest {

    private val firestore: FirebaseFirestore = mockk()
    private val usersCollection: CollectionReference = mockk()
    private val document: DocumentReference = mockk()

    private val api = UserFirestoreApi(firestore)

    /** The document key the read resolved against, captured for assertions. */
    private val documentKey = slot<String>()

    private fun givenDocument(data: Map<String, Any?>) {
        every { firestore.collection(USERS) } returns usersCollection
        every { usersCollection.document(capture(documentKey)) } returns document
        every { document.get() } returns completedTask(snapshot(data))
    }

    // ---- the four fields ------------------------------------------------

    @Test
    fun `the four premium fields survive the Users slash email decode`() = runTest {
        givenDocument(
            baseDocument() + mapOf(
                FIELD_IS_USER_PREMIUM to false,
                FIELD_SUBSCRIPTION_EXPIRY to timestamp(FUTURE_EXPIRY),
                FIELD_SUBSCRIPTION_TYPE to SubscriptionProductType.YEARLY.rawValue,
                FIELD_SUBSCRIPTION_PLATFORM to PLATFORM_ANDROID,
            ),
        )

        val user = requireNotNull(api.fetchByEmail(EMAIL))

        assertEquals("Users/{email} is the document key", EMAIL, documentKey.captured)
        assertFalse(
            "the stored bit is mapped verbatim even though it is never the gate",
            user.isPremium,
        )
        assertEquals(FUTURE_EXPIRY, user.subscriptionExpiryInstant)
        assertEquals("video_call_yearly_sub", user.subscriptionType)
        assertEquals(PLATFORM_ANDROID, user.subscriptionPlatform)
        assertEquals(
            SubscriptionProductType.YEARLY,
            SubscriptionProductType.fromRawValue(user.subscriptionType),
        )
        assertTrue("a future expiry means premium", user.isUserPremiumNow)
    }

    @Test
    fun `a document written by the iOS app decodes as premium`() = runTest {
        // iOS `UserRepository.setUserPremium` (`:170-175`) writes the same
        // three fields with `subscriptionPlatform = "iOS"` and one of the
        // two shared product ids. Premium is derived from the expiry on
        // both platforms, so this user must read as premium on Android.
        givenDocument(
            baseDocument() + mapOf(
                FIELD_IS_USER_PREMIUM to false,
                FIELD_SUBSCRIPTION_EXPIRY to timestamp(FUTURE_EXPIRY),
                FIELD_SUBSCRIPTION_TYPE to SubscriptionProductType.MONTHLY.rawValue,
                FIELD_SUBSCRIPTION_PLATFORM to PLATFORM_IOS,
            ),
        )

        val user = requireNotNull(api.fetchByEmail(EMAIL))

        assertEquals(PLATFORM_IOS, user.subscriptionPlatform)
        assertEquals(
            "the iOS and Android plan ids are byte-identical",
            SubscriptionProductType.MONTHLY,
            SubscriptionProductType.fromRawValue(user.subscriptionType),
        )
        assertTrue(
            "an iOS subscriber must read as premium on Android",
            user.isUserPremiumNow,
        )
    }

    @Test
    fun `an ISO string expiry from an older document is accepted verbatim`() = runTest {
        // The Android write path stores a Firestore `Timestamp`, but a
        // hand-edited or older document may hold the ISO string the
        // DataStore copy uses. Both must parse.
        givenDocument(
            baseDocument() + mapOf(
                FIELD_SUBSCRIPTION_EXPIRY to FUTURE_EXPIRY.toString(),
                FIELD_SUBSCRIPTION_TYPE to SubscriptionProductType.MONTHLY.rawValue,
            ),
        )

        val user = requireNotNull(api.fetchByEmail(EMAIL))

        assertEquals(FUTURE_EXPIRY.toString(), user.subscriptionExpiryDate)
        assertEquals(FUTURE_EXPIRY, user.subscriptionExpiryInstant)
        assertTrue(user.isUserPremiumNow)
    }

    // ---- the two things the decode must not get wrong -------------------

    @Test
    fun `a stale true bit with a past expiry still reads as free`() = runTest {
        // The stored `isUserPremium` bit is write-only on both platforms and
        // never flips on cancellation, so a `true` there means nothing.
        // Gating on it would strand an expired subscriber behind a paywall
        // they cannot leave.
        givenDocument(
            baseDocument() + mapOf(
                FIELD_IS_USER_PREMIUM to true,
                FIELD_SUBSCRIPTION_EXPIRY to timestamp(PAST_EXPIRY),
                FIELD_SUBSCRIPTION_TYPE to SubscriptionProductType.MONTHLY.rawValue,
            ),
        )

        val user = requireNotNull(api.fetchByEmail(EMAIL))

        assertTrue("the bit maps verbatim", user.isPremium)
        assertFalse(
            "the expiry is the only premium authority",
            user.isUserPremiumNow,
        )
    }

    @Test
    fun `a free user with no expiry decodes as free`() = runTest {
        givenDocument(baseDocument())

        val user = requireNotNull(api.fetchByEmail(EMAIL))

        assertFalse(user.isPremium)
        assertNull(user.subscriptionExpiryDate)
        assertNull(user.subscriptionExpiryInstant)
        assertNull(user.subscriptionType)
        assertNull(user.subscriptionPlatform)
        assertFalse(user.isUserPremiumNow)
    }

    @Test
    fun `an unrecognised expiry value degrades to free rather than throwing`() = runTest {
        // A malformed value from the other platform must not crash the
        // Profile screen; the `premium-entitlement` spec requires `null`.
        givenDocument(
            baseDocument() + mapOf(
                FIELD_SUBSCRIPTION_EXPIRY to "not-a-date",
                FIELD_SUBSCRIPTION_TYPE to SubscriptionProductType.MONTHLY.rawValue,
            ),
        )

        val user = requireNotNull(api.fetchByEmail(EMAIL))

        assertNull(user.subscriptionExpiryInstant)
        assertFalse(user.isUserPremiumNow)
    }

    // ---- helpers --------------------------------------------------------

    private fun baseDocument(): Map<String, Any?> = mapOf(
        FIELD_EMAIL to EMAIL,
        FIELD_NAME to "Satoshi Nakamoto",
        FIELD_NICKNAME to "satoshi",
    )

    private fun snapshot(data: Map<String, Any?>): DocumentSnapshot {
        val snapshot: DocumentSnapshot = mockk()
        every { snapshot.data } returns data
        every { snapshot.exists() } returns true
        return snapshot
    }

    /**
     * A `Task` that reports itself complete. `kotlinx.coroutines.tasks.await`
     * short-circuits on `isComplete` (fast path in the 1.9.0 sources), so
     * the read resolves without touching a dispatcher, a Looper, or a real
     * network stack.
     */
    private fun completedTask(value: DocumentSnapshot): Task<DocumentSnapshot> {
        val task: Task<DocumentSnapshot> = mockk()
        every { task.isComplete } returns true
        every { task.isCanceled } returns false
        every { task.exception } returns null
        every { task.result } returns value
        return task
    }

    private fun timestamp(instant: Instant): Timestamp =
        Timestamp(Date(instant.toEpochMilliseconds()))

    private companion object {
        const val USERS = "Users"
        const val EMAIL = "satoshi@kornerz.io"
        const val FIELD_EMAIL = "email"
        const val FIELD_NAME = "name"
        const val FIELD_NICKNAME = "nickname"
        const val FIELD_IS_USER_PREMIUM = "isUserPremium"
        const val FIELD_SUBSCRIPTION_EXPIRY = "subscriptionExpiryDate"
        const val FIELD_SUBSCRIPTION_TYPE = "subscriptionType"
        const val FIELD_SUBSCRIPTION_PLATFORM = "subscriptionPlatform"

        const val PLATFORM_ANDROID = "Android"
        const val PLATFORM_IOS = "iOS"

        val FUTURE_EXPIRY: Instant = Instant.parse("2999-01-01T00:00:00Z")
        val PAST_EXPIRY: Instant = Instant.parse("2000-01-01T00:00:00Z")
    }
}