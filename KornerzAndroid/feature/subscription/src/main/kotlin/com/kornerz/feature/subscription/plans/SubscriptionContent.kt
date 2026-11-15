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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.subscription.R
import com.kornerz.feature.subscription.plans.components.SubscriptionPlanCard

/**
 * The subscription plans screen (inventory **11.1** host + **11.2** view).
 *
 * Reproduces iOS `SubscriptionScreen.swift` (195 lines)
 * structure-for-structure: `#68B6CC` full-bleed background extending
 * behind the status bar, the 320-pt `subscription_bg` image band,
 * "Welcome {nickname}" with the `"Klouder"` fallback, the feature blurb,
 * the conditional free-trial line, exactly two plan cards, the
 * selected-price caption, a full-width black-capsule subscribe button, a
 * "Restore Purchase" text button, and the privacy-policy / term-of-use
 * link row.
 *
 * ## Layout parity
 *
 * iOS stacks these in a `VStack(spacing: 16)` with `.padding(.horizontal,
 * 32)` (`SubscriptionScreen.swift:60-72`), and nests the header copy in
 * its own `VStack(spacing: 24)` (`:38-57`). The plan cards are ordered
 * **yearly first, then monthly** (`:63-64`) — the reverse of the catalog's
 * natural order — and the header copy is centre-aligned on both axes.
 * Every one of those is reproduced here.
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
            // The aqua background is painted by the `Surface` behind this
            // column, so the screen reaches every edge. The content is
            // inset by the system bars it must clear: the band is free to
            // run under the status bar, and the links at the foot must
            // clear the navigation bar.
            .navigationBarsPadding()
            // RTL-safe: a start-anchored scroll with padding rather than
            // fixed offsets, so the mirrored locale lays out correctly
            // (phases.md Principle 7).
            .verticalScroll(rememberScrollState()),
    ) {
        // 320-pt top image band. iOS renders `Image("subscription_bg")`
        // with `.resizable().scaledToFill().clipped()` — a full-width
        // aspect-fill crop (`SubscriptionScreen.swift:28-35`).
        //
        // The asset is the iOS one: the "klouder illustration", a
        // scattering of rounded-square portraits of community members on
        // a transparent background, shipped from
        // `kornerz/Kornerz/Resources/Assets.xcassets/Auth/
        // subscription_bg.imageset/klouders_illustration (1).svg`.
        //
        // The iOS source is an SVG, which Android's `VectorDrawable`
        // cannot express: it relies on SVG `<pattern>` fills and `<mask>`
        // elements to composite the photo tiles into the rounded-square
        // frames, and `VectorDrawable` supports neither. The asset is
        // therefore rasterised from the identical SVG and shipped as a
        // 750x640 PNG (2x of the 375x320-pt source) with the alpha
        // channel intact, so the portraits sit directly on the screen's
        // aqua background exactly as they do on iOS.
        Image(
            painter = painterResource(R.drawable.bg_subscription_band),
            contentDescription = null,
            // iOS `.scaledToFill()`.
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(BAND_HEIGHT),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The screen paints edge to edge — the aqua runs behind the
                // status bar, as iOS's full-screen presentation does — so
                // the shell drops the status-bar inset for this route.
                // The content below the band insets itself here instead,
                // exactly as iOS's safe area insets the content beneath
                // the image.
                .statusBarsPadding()
                .padding(horizontal = CONTENT_PADDING),
            // iOS `VStack(spacing: 16)` (`SubscriptionScreen.swift:61`).
            verticalArrangement = Arrangement.spacedBy(STACK_SPACING),
        ) {
            // iOS `topMessageView` — a `VStack(spacing: 24)` whose inner
            // texts are centre-aligned on both axes.
            HeaderMessage(
                nickname = nickname,
                showFreeTrial = state.isTrialEligible,
            )

            // The dev flavor's application id is not a registered Play
            // listing. This is a notice *alongside* the screen chrome,
            // not an error state: the pricing, the terms links, and the
            // disabled subscribe button all still render.
            state.billingNotice?.let { notice ->
                Text(
                    text = notice,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = KornerzTypography.labelMedium.fontFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.25f))
                        .padding(horizontal = Spacing.space6, vertical = Spacing.space4),
                )
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = KornerzTypography.labelMedium.fontFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // iOS orders the cards **yearly first, then monthly**
            // (`SubscriptionScreen.swift:63-64`). The catalog arrives in
            // MONTHLY-then-YEARLY order, so it is re-sorted here rather
            // than at the data layer, which is shared with the entitlement
            // code and must stay in catalog order.
            orderedPlans(products).forEach { product ->
                SubscriptionPlanCard(
                    product = product,
                    isSelected = product.playProductId == state.selected.playProductId,
                    onClick = {
                        SubscriptionProductType.fromPlayProductId(product.playProductId)
                            ?.let(onPlanSelected)
                    },
                )
            }

            // iOS `select_payment_plan_message`, in black on the
            // aqua surface rather than the white content card.
            Text(
                text = selectedPriceCaption(state),
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = KornerzTypography.bodyMedium.fontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // iOS `try_for_free` when the user may start a trial, and
            // iOS `subscribe` when they may not.
            //
            // The two are the same button with two labels, chosen by the
            // store's **per-user** eligibility verdict — Play only
            // returns the introductory-offer phase to a user who may
            // redeem it, and StoreKit only populates
            // `Product.introductoryOffer` for an eligible user. A user
            // who has already consumed the free trial is offered
            // "Subscribe", exactly as on iOS.
            KornerzCapsuleButton(
                text = if (state.isTrialEligible) TRY_IT_FREE else SUBSCRIBE,
                onClick = onSubscribeTap,
                style = KornerzCapsuleButtonStyle.BlackOnWhite,
                enabled = !state.isPurchasing && state.billingNotice == null,
            )

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
                    .padding(vertical = Spacing.space4),
            )

            // iOS `privacy_policy` + `term_of_use`, side by side with a
            // `Spacer` between them.
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

            Spacer(Modifier.height(CONTENT_PADDING))
        }
    }
}

/**
 * iOS `topMessageView` (`SubscriptionScreen.swift:38-57`): the welcome
 * line, the feature blurb, and — only when the user is **eligible** for
 * the selected plan's free trial — the free-trial line.
 *
 * Eligibility is the store's per-user verdict, not a local timer: Play
 * only returns the introductory-offer phase to a user who may redeem it,
 * and StoreKit only populates `Product.introductoryOffer` for an
 * eligible user. A user who has already consumed the trial sees neither
 * the free-trial line nor the "Try It Free" button.
 *
 * All three are centre-aligned on both axes, matching iOS's
 * `.multilineTextAlignment(.center)` on each `Text`.
 */
@Composable
private fun HeaderMessage(
    nickname: String,
    showFreeTrial: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        // iOS `VStack(spacing: 24)`.
        verticalArrangement = Arrangement.spacedBy(HEADER_SPACING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // iOS `welcom_user`
        Text(
            text = "Welcome $nickname",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = KornerzTypography.headlineSmall.fontFamily,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        // iOS `subscription_feature_msg`
        Text(
            text = FEATURE_BLURB,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = KornerzTypography.bodyLarge.fontFamily,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        // iOS `start_free_trial` — rendered only when the *selected*
        // product has a Play free-trial offer.
        if (showFreeTrial) {
            Text(
                text = FREE_TRIAL_LINE,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = KornerzTypography.bodyMedium.fontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The two plan cards in iOS's render order: **yearly, then monthly**
 * (`SubscriptionScreen.swift:63-64`).
 *
 * A plan outside the pinned catalog keeps its relative position after the
 * two known ones rather than disappearing, so a misconfigured listing
 * still shows the user something actionable.
 */
private fun orderedPlans(products: List<SubscriptionProduct>): List<SubscriptionProduct> {
    val yearly = products.filter { it.isYearly }
    val monthly = products.filter { it.productType == SubscriptionProductType.MONTHLY }
    val others = products.filter {
        it.productType != SubscriptionProductType.YEARLY &&
            it.productType != SubscriptionProductType.MONTHLY
    }
    return yearly + monthly + others
}

/**
 * "1 week free, then {price}" when the user is eligible for the
 * selected plan's trial, else just the price. iOS passes
 * `viewModel.selectedPrice` into `select_payment_plan_message`, whose
 * English value carries the "1 week free, then " prefix.
 *
 * The prefix is gated on the same eligibility flag as the button label,
 * so the caption can never claim a trial the button does not offer.
 *
 * The price itself is the card's `displayPrice` — iOS's
 * `<displayPrice>/<periodUnit>` format (`SubscriptionViewModel.swift:93-110`)
 * — so the caption reads "1 week free, then $10.99/month" exactly as it
 * does on iOS.
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

/**
 * The yearly plan selected. Pins the iOS render order (yearly card above
 * the monthly card) and the two-line yearly card.
 */
@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND)
@Composable
private fun SubscriptionContentYearlySelectedPreview() {
    SubscriptionContent(
        state = previewState().copy(selected = SubscriptionProductType.YEARLY),
        nickname = "satoshi",
        onPlanSelected = {},
        onSubscribeTap = {},
        onRestoreTap = {},
        onPrivacyPolicyTap = {},
        onTermsOfUseTap = {},
        onRetry = {},
    )
}

/**
 * The user is **not** trial-eligible — they have already redeemed the
 * free trial, or the store is not granting one to them.
 *
 * Pins the button contract: the capsule reads "Subscribe", not "Try It
 * Free", and the price caption carries no "1 week free, then " prefix.
 * This is the state iOS reaches through the same route — StoreKit simply
 * leaves `Product.introductoryOffer` nil for a user who has consumed it
 * (`SubscriptionViewModel.swift:73-79`).
 */
@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND)
@Composable
private fun SubscriptionContentNoTrialPreview() {
    SubscriptionContent(
        state = previewState(isTrialEligible = false),
        nickname = "satoshi",
        onPlanSelected = {},
        onSubscribeTap = {},
        onRestoreTap = {},
        onPrivacyPolicyTap = {},
        onTermsOfUseTap = {},
        onRetry = {},
    )
}

private fun previewState(isTrialEligible: Boolean = true) = SubscriptionState(
    products = AsyncValue.Success(
        listOf(
            SubscriptionProduct(
                playProductId = SubscriptionProductType.MONTHLY.playProductId,
                subscriptionType = SubscriptionProductType.MONTHLY.subscriptionType,
                title = SubscriptionProductType.MONTHLY.label,
                priceLabel = "$10.99",
                periodLabel = "per month",
                isTrialEligible = isTrialEligible,
                offerTag = "offer-token-monthly",
            ),
            SubscriptionProduct(
                playProductId = SubscriptionProductType.YEARLY.playProductId,
                subscriptionType = SubscriptionProductType.YEARLY.subscriptionType,
                title = SubscriptionProductType.YEARLY.label,
                priceLabel = "$95.90",
                periodLabel = "per year",
                pricePerMonthEquivalent = "$7.99/month",
                isTrialEligible = isTrialEligible,
            ),
        ),
    ),
    isTrialEligible = isTrialEligible,
)

private const val PREVIEW_BACKGROUND = 0xFF68B6CC

private val BAND_HEIGHT = 320.dp

/** iOS `.padding(.horizontal, 32)`. */
private val CONTENT_PADDING = Spacing.space16

/** iOS `VStack(spacing: 16)`. */
private val STACK_SPACING = Spacing.space8

/** iOS `topMessageView`'s `VStack(spacing: 24)`. */
private val HEADER_SPACING = Spacing.space12

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
