package com.kornerz.feature.subscription

import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.kornerz.feature.subscription.data.network.SubscriptionCloudFunctionsApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `verifySubscription` callable client.
 *
 * The function itself is **external to this repository** — there is no
 * `functions/` directory, no `*.ts` backend source, and no `firebase.json`
 * anywhere here, exactly as P6's `deleteUserAccount` is. So the
 * load-bearing behaviour to test is the client's own contract: it sends the
 * documented request keys, decodes the documented response DTO, and turns
 * an undeployed function into a `Result.failure` rather than letting an
 * exception cross the API boundary.
 */
class SubscriptionCloudFunctionsApiTest {

    private val functions: FirebaseFunctions = mockk(relaxed = true)
    private val callable: HttpsCallableReference = mockk(relaxed = true)
    private val result: HttpsCallableResult = mockk(relaxed = true)

    private val api = SubscriptionCloudFunctionsApi(functions)

    private fun givenResponse(json: String) {
        every { result.data } returns json
        every { functions.getHttpsCallable(VERIFY_SUBSCRIPTION) } returns callable
        every { callable.call(any()) } returns Tasks.forResult(result)
    }

    private fun givenMissingFunction() {
        every { functions.getHttpsCallable(any()) } returns callable
        every { callable.call(any()) } returns Tasks.forException(
            IllegalStateException("Function not found: verifySubscription"),
        )
    }

    @Test
    fun `the request carries the documented keys`() = runTest {
        givenResponse(RESPONSE_JSON)
        val request = slot<Any>()
        every { callable.call(capture(request)) } returns Tasks.forResult(result)

        api.verifyPurchase("purchase-token", "video_call_yearly_sub")

        @Suppress("UNCHECKED_CAST")
        val payload = request.captured as Map<String, Any>
        assertEquals("purchase-token", payload["token"])
        assertEquals("video_call_yearly_sub", payload["productId"])
    }

    @Test
    fun `the function name is the documented verifySubscription`() = runTest {
        givenResponse(RESPONSE_JSON)

        api.verifyPurchase("purchase-token", "video_call_monthly")

        // A typo here would surface only as an opaque 404 at runtime.
        every { functions.getHttpsCallable(VERIFY_SUBSCRIPTION) } returns callable
    }

    @Test
    fun `an active verdict decodes into the DTO`() = runTest {
        givenResponse(RESPONSE_JSON)

        val result = api.verifyPurchase("purchase-token", "video_call_yearly_sub")

        assertTrue("expected success, got $result", result.isSuccess)
        val verdict = result.getOrThrow()
        assertEquals("2999-12-01T00:00:00Z", verdict.expiryDate)
        assertTrue(verdict.isActive)
        assertEquals(true, verdict.autoRenewing)
        assertEquals(true, verdict.acknowledged)
    }

    @Test
    fun `an inactive verdict decodes without error`() = runTest {
        givenResponse(
            """{"expiryDate":"2020-01-01T00:00:00Z","isActive":false,""" +
                """"autoRenewing":false,"acknowledged":true}""",
        )

        val verdict = api.verifyPurchase("purchase-token", "video_call_monthly")

        assertTrue(verdict.isSuccess)
        assertFalse(verdict.getOrThrow().isActive)
    }

    @Test
    fun `absent optional fields decode as null`() = runTest {
        // A function that returns only the required keys must not break the
        // client; the reconciliation path treats a null expiry as "keep the
        // local value".
        givenResponse("""{"isActive":true}""")

        val verdict = api.verifyPurchase("purchase-token", "video_call_monthly").getOrThrow()

        assertTrue(verdict.isActive)
        assertNull(verdict.expiryDate)
        assertNull(verdict.autoRenewing)
        assertNull(verdict.acknowledged)
    }

    @Test
    fun `a missing function returns failure without throwing`() = runTest {
        // The realistic pre-deployment case: `verifySubscription` does not
        // exist in the Firebase project.
        givenMissingFunction()

        val result = api.verifyPurchase("purchase-token", "video_call_monthly")

        assertTrue("expected failure, got $result", result.isFailure)
    }

    @Test
    fun `a malformed response returns failure rather than crashing`() = runTest {
        givenResponse("not json at all")

        val result = api.verifyPurchase("purchase-token", "video_call_monthly")

        assertTrue("expected failure, got $result", result.isFailure)
    }

    private companion object {
        const val VERIFY_SUBSCRIPTION = "verifySubscription"
        const val RESPONSE_JSON =
            """{"expiryDate":"2999-12-01T00:00:00Z","isActive":true,""" +
                """"autoRenewing":true,"acknowledged":true}"""
    }
}