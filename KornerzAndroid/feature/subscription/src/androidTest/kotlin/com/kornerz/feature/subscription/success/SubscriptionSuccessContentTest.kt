package com.kornerz.feature.subscription.success

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kornerz.feature.subscription.plans.SubscriptionContentTest
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **Skeleton only** (task 15.7) — see [SubscriptionContentTest] for why
 * each case is `@Ignore`d.
 *
 * The success screen is 43 lines of iOS and roughly 80 of Kotlin, so its
 * structural coverage is three assertions: the message, the "Done" button,
 * and the absence of a back affordance. It performs no Firestore write of
 * its own, so there is no state to assert here at all.
 */
@RunWith(AndroidJUnit4::class)
class SubscriptionSuccessContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Ignore("no instrumentation harness — see SubscriptionContentTest")
    @Test
    fun rendersTheActivatedMessage() {
        composeTestRule.setContent {
            SubscriptionSuccessContent(onDoneTap = {})
        }

        composeTestRule.onNodeWithText("Subscription activated successfully").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Subscription activated").assertIsDisplayed()
    }

    @Ignore("no instrumentation harness — see SubscriptionContentTest")
    @Test
    fun doneEmitsDismissed() {
        var done = false
        composeTestRule.setContent {
            SubscriptionSuccessContent(onDoneTap = { done = true })
        }

        composeTestRule.onNodeWithText("Done").performClick()

        // The event is `SubscriptionSuccessNavEvent.Dismissed`, which the
        // shell turns into a back-pop plus `PremiumGate.markSatisfied()`.
        assertTrue("Done must dismiss the flow", done)
    }
}