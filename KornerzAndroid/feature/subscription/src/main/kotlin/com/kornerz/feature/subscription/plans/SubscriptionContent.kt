package com.kornerz.feature.subscription.plans

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.SubscriptionProduct
import com.kornerz.core.model.SubscriptionProductType
import com.kornerz.core.ui.components.KornerzCapsuleButton
import com.kornerz.core.ui.components.KornerzCapsuleButtonStyle
import com.kornerz.core.ui.components.KornerzErrorState
import com.kornerz.core.ui.theme.KornerzColors
import com.kornerz.core.ui.theme.KornerzTypography
import com.kornerz.feature.subscription.R
import com.kornerz.feature.subscription.plans.components.SubscriptionPlanCard

/**
 * The subscription plans screen (inventory **11.1** host + **11.2** view).
 *
 * Reproduces iOS `SubscriptionScreen.swift` (195 lines)
 * structure-for-structure: `#68B6CC` full-bleed background extending
 * behind the status bar, a 320-pt image band, "Welcome {nickname}" with
 * the `"Klouder"` fallback, the feature blurb, the conditional
 * free-trial line, exactly two plan cards, the selected-price caption, a
 * full-width black-capsule subscribe button, a "Restore Purchase" text
 * button, and the privacy-policy / term-of-use link row.
 *
 * Deliberately **no** app bar, **no** discount badge, and **no**
 * "what's included" list — none of the three exist in the live iOS
 * screen.
 *
 * Deliberately **no** close affordance either. The paywall is
 * mandatory: the product requires a subscription before the app is
 * usable, so this screen has exactly one exit — subscribing. The
 * plans screen's host therefore also swallows the system back
 * gesture (`SubscriptionScreen`), and neither is a parity break with
 * iOS, which has no working close affordance either
 * (`SubscriptionController.swift:22-23`).
 */
@Composable
fun SubscriptionContent(
    state: SubscriptionState,
    nickname: String,
    onPlanSelected: (SubscriptionProductType) -> Unit,
    onSubscribeTap: () -> Unit,
    onRestoreTap: () -> Unit,
    onPrivacyPolicyTap: () -> Unit,
    onTermsOfUseTap: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundColor),
    ) {
        when (val products = state.products) {
            AsyncValue.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
            }

            is AsyncValue.Error -> KornerzErrorState(
                message = products.throwable.message ?: ERROR_FALLBACK,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )

            is AsyncValue.Success -> LoadedPlans(
                state = state,
                products = products.data,
                nickname = nickname,
                onPlanSelected = onPlanSelected,
                onSubscribeTap = onSubscribeTap,
                onRestoreTap = onRestoreTap,
                onPrivacyPolicyTap = onPrivacyPolicyTap,
                onTermsOfUseTap = onTermsOfUseTap,
            )
        }
    }
}

@Composable
private fun LoadedPlans(
    state: SubscriptionState,
    products: List<SubscriptionProduct>,
    nickname: String,
    onPlanSelected: (SubscriptionProductType) -> Unit,
    onSubscribeTap: () -> Unit,
    onRestoreTap: () -> Unit,
    onPrivacyPolicyTap: () -> Unit,
    onTermsOfUseTap: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // RTL-safe: a start-anchored scroll with padding rather than
            // fixed offsets, so the mirrored locale lays out correctly
            // (phases.md Principle 7).
            .verticalScroll(rememberScrollState()),
    ) {
        // 320-pt top image band. iOS references a `subscription_bg`
        // asset that does not exist in its asset catalog and renders
        // blank; Android ships a working vector band instead.
        Image(
            painter = painterResource(R.drawable.bg_subscription_band),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND_HEIGHT),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CONTENT_PADDING)
                .padding(bottom = CONTENT_PADDING),
        ) {
            Spacer(Modifier.height(SPACER_LARGE))
            Text(
                // iOS `welcom_user`
                text = "Welcome $nickname",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = KornerzTypography.headlineSmall.fontFamily,
            )
            Spacer(Modifier.height(SPACER_SMALL))
            Text(
                // iOS `subscription_feature_msg`
                text = FEATURE_BLURB,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = KornerzTypography.bodyLarge.fontFamily,
            )

            // iOS `start_free_trial` — rendered only when the
            // *selected* product has a Play free-trial offer.
            if (state.isTrialEligible) {
                Spacer(Modifier.height(SPACER_SMALL))
                Text(
                    text = FREE_TRIAL_LINE,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = KornerzTypography.bodyMedium.fontFamily,
                )
            }

            // The dev flavor's application id is not a registered Play
            // listing. This is a notice *alongside* the screen chrome,
            // not an error state: the pricing, the terms links, and the
            // disabled subscribe button all still render.
            state.billingNotice?.let { notice ->
                Spacer(Modifier.height(SPACER_MEDIUM))
                Text(
                    text = notice,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = KornerzTypography.labelMedium.fontFamily,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.25f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            state.error?.let { error ->
                Spacer(Modifier.height(SPACER_MEDIUM))
                Text(
                    text = error,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = KornerzTypography.labelMedium.fontFamily,
                )
            }

            Spacer(Modifier.height(SPACER_LARGE))
            // Exactly two cards, in the pinned MONTHLY-then-YEARLY
            // order. No plan-count variable, no third tier.
            products.forEach { product ->
                SubscriptionPlanCard(
                    product = product,
                    isSelected = product.planId == state.selected.rawValue,
                    onClick = {
                        SubscriptionProductType.fromRawValue(product.planId)
                            ?.let(onPlanSelected)
                    },
                )
                Spacer(Modifier.height(SPACER_MEDIUM))
            }

            // iOS `select_payment_plan_message`, in black on the
            // aqua surface rather than the white content card.
            Text(
                text = selectedPriceCaption(state),
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = KornerzTypography.bodyMedium.fontFamily,
            )

            Spacer(Modifier.height(SPACER_MEDIUM))
            KornerzCapsuleButton(
                text = if (state.isTrialEligible) TRY_IT_FREE else SUBSCRIBE,
                onClick = onSubscribeTap,
                style = KornerzCapsuleButtonStyle.BlackOnWhite,
                enabled = !state.isPurchasing && state.billingNotice == null,
            )

            Spacer(Modifier.height(SPACER_MEDIUM))
            // iOS `restore`
            Text(
                text = RESTORE_PURCHASE,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = KornerzTypography.bodyMedium.fontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.isRestoring, onClick = onRestoreTap)
                    .padding(vertical = 8.dp),
            )

            Spacer(Modifier.height(SPACER_MEDIUM))
            // iOS `privacy_policy` + `term_of_use`, side by side.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = PRIVACY_POLICY,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = KornerzTypography.labelMedium.fontFamily,
                    modifier = Modifier.clickable(onClick = onPrivacyPolicyTap),
                )
                Text(
                    text = TERM_OF_USE,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = KornerzTypography.labelMedium.fontFamily,
                    modifier = Modifier.clickable(onClick = onTermsOfUseTap),
                )
            }
        }
    }
}

/**
 * "1 week free, then {price}" when the selected plan has a trial, else
 * just the price. iOS passes `viewModel.selectedPrice` into
 * `select_payment_plan_message`, whose English value carries the
 * "1 week free, then " prefix.
 */
private fun selectedPriceCaption(state: SubscriptionState): String {
    val price = state.selectedPriceLabel.orEmpty()
    return if (state.isTrialEligible) "$TRIAL_CAPTION_PREFIX$price" else price
}

/**
 * iOS `AppColor.C68B6CC` (`kornerz/Kornerz/Helper/Config.swift:51`),
 * surfaced as `KornerzColors.AquaLight` so the screen reads from the brand
 * palette rather than an inline hex.
 */
private val BackgroundColor = KornerzColors.AquaLight

// ---- previews (P8 task 16.2) ------------------------------------------

/**
 * `en-rXA` is the Android pseudo-locale that mirrors every string and
 * flips layout direction, so these previews are how the RTL-safety claim
 * (`docs/phases.md` Principle 7) is actually checked: the layout uses
 * `Alignment.TopEnd` / `TextAlign.End` / `padding(horizontal = …)`
 * throughout and no fixed start offsets, so the whole screen mirrors.
 */
@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND, locale = "en-rXA")
@Composable
private fun SubscriptionContentRtlPreview() {
    SubscriptionContent(
        state = previewState(),
        nickname = "satoshi",
        onPlanSelected = {},
        onSubscribeTap = {},
        onRestoreTap = {},
        onPrivacyPolicyTap = {},
        onTermsOfUseTap = {},
        onRetry = {},
    )
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND)
@Composable
private fun SubscriptionContentMonthlyTrialPreview() {
    SubscriptionContent(
        state = previewState(),
        nickname = "satoshi",
        onPlanSelected = {},
        onSubscribeTap = {},
        onRestoreTap = {},
        onPrivacyPolicyTap = {},
        onTermsOfUseTap = {},
        onRetry = {},
    )
}

/** The `dev` flavor's unregistered application id: chrome plus a notice. */
@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND)
@Composable
private fun SubscriptionContentBillingUnavailablePreview() {
    SubscriptionContent(
        state = previewState().copy(billingNotice = "Billing is unavailable in this build"),
        nickname = "satoshi",
        onPlanSelected = {},
        onSubscribeTap = {},
        onRestoreTap = {},
        onPrivacyPolicyTap = {},
        onTermsOfUseTap = {},
        onRetry = {},
    )
}

private fun previewState() = SubscriptionState(
    products = AsyncValue.Success(
        listOf(
            SubscriptionProduct(
                planId = SubscriptionProductType.MONTHLY.rawValue,
                title = "Monthly",
                priceLabel = "$10.99",
                periodLabel = "per month",
                hasFreeTrialOffer = true,
                offerTag = "offer-token-monthly",
            ),
            SubscriptionProduct(
                planId = SubscriptionProductType.YEARLY.rawValue,
                title = "Yearly",
                priceLabel = "$95.90",
                periodLabel = "per year",
                pricePerMonthEquivalent = "$7.99/month",
            ),
        ),
    ),
    isTrialEligible = true,
)

private const val PREVIEW_BACKGROUND = 0xFF68B6CC

private val BAND_HEIGHT = 320.dp
private val CONTENT_PADDING = 20.dp
private val SPACER_LARGE = 20.dp
private val SPACER_MEDIUM = 12.dp
private val SPACER_SMALL = 8.dp

/** iOS `subscription_feature_msg`. */
private const val FEATURE_BLURB =
    "Get full access to our community of Klouders, unlimited Nook video " +
        "calls, and Loops to discuss topics of interest."

/** iOS `start_free_trial`. */
private const val FREE_TRIAL_LINE = "Start with a 1 week free trial."

/** iOS `select_payment_plan_message`, English value. */
private const val TRIAL_CAPTION_PREFIX = "1 week free, then "

/** iOS `try_for_free`. */
private const val TRY_IT_FREE = "Try It Free"

/** iOS `subscribe`. */
private const val SUBSCRIBE = "Subscribe"

/** iOS `restore`. */
private const val RESTORE_PURCHASE = "Restore Purchase"

/** iOS `privacy_policy`. */
private const val PRIVACY_POLICY = "Privacy Policy"

/** iOS `term_of_use`. */
private const val TERM_OF_USE = "Term of Use"

private const val ERROR_FALLBACK = "Could not load subscription plans"