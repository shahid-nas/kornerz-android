package com.kornerz.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The catalog ids are the cross-platform contract: `SubscriptionProductType.rawValue`
 * is written verbatim to `Users/{email}.subscriptionType`, and iOS's
 * `InAppManager.InAppProduct`
 * (`kornerz/Kornerz/InAppPurchase/InAppManager.swift:16-19`) uses the same two
 * strings. An accidental rename here would silently orphan every existing
 * subscriber's plan label, and nothing at build time would notice — so these
 * literals are asserted explicitly.
 */
class SubscriptionProductTypeTest {

    @Test
    fun `raw values are byte-identical to iOS InAppProduct`() {
        assertEquals("video_call_monthly", SubscriptionProductType.MONTHLY.rawValue)
        assertEquals("video_call_yearly_sub", SubscriptionProductType.YEARLY.rawValue)
    }

    @Test
    fun `catalog is exactly the two iOS products, monthly first`() {
        assertEquals(
            listOf(SubscriptionProductType.MONTHLY, SubscriptionProductType.YEARLY),
            SubscriptionProductType.entries.toList(),
        )
        assertEquals(SubscriptionProductType.entries.toList(), SubscriptionProductType.ALL)
    }

    @Test
    fun `fromRawValue resolves both known ids`() {
        assertEquals(
            SubscriptionProductType.MONTHLY,
            SubscriptionProductType.fromRawValue("video_call_monthly"),
        )
        assertEquals(
            SubscriptionProductType.YEARLY,
            SubscriptionProductType.fromRawValue("video_call_yearly_sub"),
        )
    }

    @Test
    fun `fromRawValue returns null for absent or unknown values`() {
        // A null must degrade to the expiry fallback rather than throw —
        // the document may have been written by another client.
        assertNull(SubscriptionProductType.fromRawValue(null))
        assertNull(SubscriptionProductType.fromRawValue(""))
        assertNull(SubscriptionProductType.fromRawValue("lifetime"))
        assertNull(SubscriptionProductType.fromRawValue("VIDEO_CALL_MONTHLY"))
    }
}