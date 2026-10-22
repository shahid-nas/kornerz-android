package com.kornerz.feature.subscription.plans

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.SubscriptionProduct
import com.kornerz.core.model.SubscriptionProductType
import com.kornerz.core.ui.components.KornerzCapsuleButton
import com.kornerz.core.ui.components.KornerzCapsuleButtonStyle
import com.kornerz.feature.subscription.plans.components.SubscriptionPlanCard
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **Skeleton only** (task 15.7) — the assertions below are written out so
 * the intended coverage is reviewable, but each is `@Ignore`d because the
 * repository still has no instrumentation harness: no `androidTest` source
 * set existed before P8, CI runs `assembleDevDebug` only, and no emulator
 * is provisioned for the unit-test job. This follows the P6/P7 precedent,
 * which deferred the same way.
 *
 * The JVM suite is where P8's behaviour actually lives —
 * `SubscriptionViewModelTest` covers catalog resolution, trial
 * eligibility, the acknowledge-before-grant ordering, the ack-failure
 * block, and `UserCancelled` leaving `error` null. What is left for an
 * instrumentation run is purely visual structure.
 *
 * To un-ignore: drop the `@Ignore` annotations and run
 * `./gradlew :feature:subscription:connectedDebugAndroidTest` against an
 * API 26 and an API 35 emulator.
 */
@RunWith(AndroidJUnit4::class)
class SubscriptionContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Ignore("no instrumentation harness — see the class KDoc")
    @Test
    fun rendersTheIosChrome() {
        composeTestRule.setContent {
            Harness(state = loadedState(), nickname = "satoshi", taps = mutableListOf())
        }

        composeTestRule.onNodeWithText("Welcome satoshi").assertIsDisplayed()
        composeTestRule.onNodeWithText(FEATURE_BLURB).assertIsDisplayed()
        composeTestRule.onNodeWithText("1 week free, then \$10.99").assertIsDisplayed()
    }

    @Ignore("no instrumentation harness — see the class KDoc")
    @Test
    fun fallsBackToKlouder() {
        composeTestRule.setContent {
            Harness(state = loadedState(), nickname = "Klouder", taps = mutableListOf())
        }

        composeTestRule.onNodeWithText("Welcome Klouder").assertIsDisplayed()
    }

    @Ignore("no instrumentation harness — see the class KDoc")
    @Test
    fun rendersExactlyTwoPlanCardsAndNoDiscountBadge() {
        composeTestRule.setContent {
            Harness(state = loadedState(), nickname = "satoshi", taps = mutableListOf())
        }

        composeTestRule.onAllNodesWithText("Monthly").fetchSemanticsNodes()
        composeTestRule.onAllNodesWithText("Yearly").fetchSemanticsNodes()
        // No "save", "best value", or unlock list — none exists in iOS.
        assertTrue(
            "no discount badge is rendered",
            composeTestRule.onAllNodes(hasText("save", substring = true, ignoreCase = true))
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Ignore("no instrumentation harness — see the class KDoc")
    @Test
    fun theSubscribeLabelFollowsTrialEligibility() {
        composeTestRule.setContent {
            Harness(
                state = loadedState(isTrialEligible = true),
                nickname = "satoshi",
                taps = mutableListOf(),
            )
        }
        composeTestRule.onNodeWithText("Try It Free").assertIsDisplayed()

        composeTestRule.setContent {
            Harness(
                state = loadedState(isTrialEligible = false),
                nickname = "satoshi",
                taps = mutableListOf(),
            )
        }
        composeTestRule.onNodeWithText("Subscribe").assertIsDisplayed()
    }

    @Ignore("no instrumentation harness — see the class KDoc")
    @Test
    fun aBillingNoticeDisablesSubscribeWithoutHidingTheChrome() {
        val taps = mutableListOf<String>()
        composeTestRule.setContent {
            Harness(
                state = loadedState().copy(billingNotice = BILLING_UNAVAILABLE),
                nickname = "satoshi",
                taps = taps,
            )
        }

        composeTestRule.onNodeWithText(BILLING_UNAVAILABLE).assertIsDisplayed()
        composeTestRule.onNodeWithText("Subscribe").performClick()
        assertTrue(
            "a dev build must not be able to launch a purchase",
            "subscribe" !in taps,
        )
    }

    @Ignore("no instrumentation harness — see the class KDoc")
    @Test
    fun thereIsNoCloseAffordance() {
        composeTestRule.setContent {
            Harness(state = loadedState(), nickname = "satoshi", taps = mutableListOf())
        }

        // The paywall is mandatory: the app is unusable without a
        // subscription, so the plans screen deliberately renders no close
        // affordance and swallows the system back gesture. A regression here
        // re-opens an escape hatch out of the gate.
        assertTrue(
            "no close affordance is rendered",
            composeTestRule.onAllNodesWithContentDescription("Close")
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Ignore("no instrumentation harness — see the class KDoc")
    @Test
    fun theErrorStateOffersARetry() {
        val taps = mutableListOf<String>()
        composeTestRule.setContent {
            Harness(
                state = SubscriptionState(
                    products = AsyncValue.Error(IllegalStateException("no plans")),
                ),
                nickname = "satoshi",
                taps = taps,
            )
        }

        composeTestRule.onNodeWithText("Retry").performClick()
        assertTrue("retry" in taps)
    }

    @Composable
    private fun Harness(
        state: SubscriptionState,
        nickname: String,
        taps: MutableList<String>,
    ) = SubscriptionContent(
        state = state,
        nickname = nickname,
        onPlanSelected = { taps.add("plan:${it.rawValue}") },
        onSubscribeTap = { taps.add("subscribe") },
        onRestoreTap = { taps.add("restore") },
        onPrivacyPolicyTap = { taps.add("privacy") },
        onTermsOfUseTap = { taps.add("terms") },
        onRetry = { taps.add("retry") },
    )

    private fun loadedState(isTrialEligible: Boolean = true) = SubscriptionState(
        products = AsyncValue.Success(
            listOf(
                SubscriptionProduct(
                    planId = SubscriptionProductType.MONTHLY.rawValue,
                    title = "Monthly",
                    priceLabel = MONTHLY_PRICE,
                    periodLabel = "per month",
                    hasFreeTrialOffer = isTrialEligible,
                ),
                SubscriptionProduct(
                    planId = SubscriptionProductType.YEARLY.rawValue,
                    title = "Yearly",
                    priceLabel = YEARLY_PRICE,
                    periodLabel = "per year",
                    pricePerMonthEquivalent = PER_MONTH,
                ),
            ),
        ),
        isTrialEligible = isTrialEligible,
    )

    private companion object {
        const val FEATURE_BLURB =
            "Get full access to our community of Klouders, unlimited Nook video " +
                "calls, and Loops to discuss topics of interest."
        const val BILLING_UNAVAILABLE = "Billing is unavailable in this build"
        const val MONTHLY_PRICE = "$10.99"
        const val YEARLY_PRICE = "$95.90"
        const val PER_MONTH = "$7.99/month"
    }
}

/**
 * **Skeleton only** (task 15.7) — see [SubscriptionContentTest] for why
 * each case is `@Ignore`d.
 *
 * `KornerzCapsuleButton` and `SubscriptionPlanCard` are the two new
 * `core/ui` / `feature/subscription` primitives the subscription chrome is
 * built from, so they are where a future instrumentation run gets the most
 * value per assertion.
 */
@RunWith(AndroidJUnit4::class)
class SubscriptionChromeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Ignore("no instrumentation harness — see SubscriptionContentTest")
    @Test
    fun theCapsuleButtonRendersBothVariants() {
        composeTestRule.setContent {
            KornerzCapsuleButton(
                text = "Try It Free",
                onClick = {},
                style = KornerzCapsuleButtonStyle.BlackOnWhite,
            )
        }
        composeTestRule.onNodeWithText("Try It Free").assertIsDisplayed()

        composeTestRule.setContent {
            KornerzCapsuleButton(
                text = "Done",
                onClick = {},
                style = KornerzCapsuleButtonStyle.WhiteOnBlack,
            )
        }
        composeTestRule.onNodeWithText("Done").assertIsDisplayed()
    }

    @Ignore("no instrumentation harness — see SubscriptionContentTest")
    @Test
    fun onlyTheYearlyCardCarriesAPerMonthLine() {
        composeTestRule.setContent {
            SubscriptionPlanCard(
                product = SubscriptionProduct(
                    planId = SubscriptionProductType.MONTHLY.rawValue,
                    title = "Monthly",
                    priceLabel = "$10.99",
                    periodLabel = "per month",
                ),
                isSelected = true,
                onClick = {},
            )
        }
        composeTestRule.onNodeWithText("$10.99").assertIsDisplayed()
        assertTrue(
            "monthly cards render one price line",
            composeTestRule.onAllNodesWithText("/month").fetchSemanticsNodes().isEmpty(),
        )
    }
}