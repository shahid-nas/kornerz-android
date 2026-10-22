package com.kornerz.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `pricePerMonthEquivalent` is the grey secondary line on the yearly card
 * and must be absent on the monthly one — the plans screen renders it
 * unconditionally when non-null, so a spurious non-null monthly value would
 * put a "/month" line under a per-month price.
 */
class SubscriptionProductTest {

    @Test
    fun `monthly product has no per-month equivalent line`() {
        val product = monthly().copy(pricePerMonthEquivalent = null)
        assertNull(product.pricePerMonthEquivalent)
        assertFalse(product.isYearly)
    }

    @Test
    fun `yearly product carries the per-month equivalent line`() {
        val product = yearly().copy(pricePerMonthEquivalent = "$7.99/month")
        assertEquals("$7.99/month", product.pricePerMonthEquivalent)
        assertTrue(product.isYearly)
    }

    @Test
    fun `productType resolves from the planId`() {
        assertEquals(
            SubscriptionProductType.MONTHLY,
            monthly().productType,
        )
        assertEquals(
            SubscriptionProductType.YEARLY,
            yearly().productType,
        )
    }

    @Test
    fun `a product id outside the pinned catalog resolves to null and is not yearly`() {
        val product = monthly().copy(planId = "unrecognised")
        assertNull(product.productType)
        assertFalse(product.isYearly)
    }

    @Test
    fun `SubscriptionPlan projects the underlying product`() {
        val plan = SubscriptionPlan(SubscriptionProductType.YEARLY, yearly())
        assertEquals("video_call_yearly_sub", plan.planId)
        assertEquals("$95.90", plan.priceLabel)
        assertEquals("$7.99/month", plan.pricePerMonthEquivalent)
        assertTrue(plan.hasFreeTrialOffer)
        assertEquals("yearly-offer-token", plan.offerTag)
    }

    private fun monthly() = SubscriptionProduct(
        planId = SubscriptionProductType.MONTHLY.rawValue,
        title = "Monthly",
        priceLabel = "$10.99",
        periodLabel = "per month",
        pricePerMonthEquivalent = null,
        hasFreeTrialOffer = true,
        offerTag = "monthly-offer-token",
    )

    private fun yearly() = SubscriptionProduct(
        planId = SubscriptionProductType.YEARLY.rawValue,
        title = "Yearly",
        priceLabel = "$95.90",
        periodLabel = "per year",
        pricePerMonthEquivalent = "$7.99/month",
        hasFreeTrialOffer = true,
        offerTag = "yearly-offer-token",
    )
}