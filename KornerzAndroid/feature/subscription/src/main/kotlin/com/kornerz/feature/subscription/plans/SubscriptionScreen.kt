package com.kornerz.feature.subscription.plans

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kornerz.core.model.IAP_ENABLED
import com.kornerz.core.ui.theme.KornerzColors
import com.kornerz.feature.subscription.data.billing.SubscriptionBillingGateway
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The plans-screen host (inventory **11.1**).
 *
 * Mirrors iOS `SubscriptionController` (`:17-37`): it constructs the
 * ViewModel, embeds the view, and owns the Activity that Play needs in
 * order to present its purchase sheet.
 *
 * The paywall is **mandatory** — the product requires a subscription
 * before the app is usable — so this screen has no way out other than
 * subscribing. It renders no close affordance (see
 * [SubscriptionContent]) and swallows the system back gesture, so the
 * plans route can never be popped by the user. The only pop is the
 * success screen's, which unwinds past this route.
 */
@Composable
fun SubscriptionScreen(
    onNavEvent: (SubscriptionNavEvent) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SubscriptionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nickname by viewModel.nickname.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val applicationContext = remember(context) { context.applicationContext }
    val billingGateway = remember(applicationContext) {
        EntryPointAccessors.fromApplication(
            applicationContext,
            BillingClientApiEntryPoint::class.java,
        ).billingGateway()
    }

    // Play's `launchBillingFlow` needs an Activity, which the injected
    // application context cannot supply. The Compose layer owns the
    // lifecycle, so the Activity is attached here and released on dispose;
    // the real gateway holds the reference weakly, so a finished Activity
    // is never retained by that singleton. The `dev` gateway ignores both
    // calls — it presents no UI.
    DisposableEffect(context) {
        (context as? Activity)?.let(billingGateway::attachActivity)
        onDispose {
            billingGateway.detachActivity()
            viewModel.onScreenDisposed()
        }
    }

    // Swallow the system back gesture. Registering a no-op `BackHandler`
    // consumes the event, so the gesture cannot pop this route and drop the
    // user into the un-gated app. The handler stays enabled even mid-purchase
    // so the Play sheet's dismissal can never route out through here.
    //
    // TEMPORARY: while IAP is off there is no purchase to complete, so the
    // paywall must not be a dead end — the gesture pops the route instead.
    BackHandler(enabled = IAP_ENABLED) { }

    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect(onNavEvent)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = KornerzColors.AquaLight,
    ) {
        SubscriptionContent(
            state = state,
            nickname = nickname,
            onPlanSelected = viewModel::onPlanSelected,
            onSubscribeTap = viewModel::onSubscribeTap,
            onRestoreTap = viewModel::onRestoreTap,
            onPrivacyPolicyTap = viewModel::onPrivacyPolicyTap,
            onTermsOfUseTap = viewModel::onTermsOfUseTap,
            onRetry = viewModel::loadCatalog,
        )
    }
}

/**
 * Grants the screen access to the `@Singleton` billing gateway without
 * making it a constructor dependency of every shell composable.
 *
 * A local `@EntryPoint` is used rather than passing the wrapper down from
 * `AppShell`, because `feature/shell` must not depend on
 * `feature/subscription` (`docs/architecture.md` §2.5 — only `app` imports
 * the feature modules); the shell reaches these screens solely through
 * `SubscriptionRoute`.
 *
 * Installed in [SingletonComponent] because the screen resolves it with
 * `EntryPointAccessors.fromApplication`, which can only see entry points in
 * the application component.
 *
 * Returns the [SubscriptionBillingGateway] rather than `BillingClientApi`
 * so that the `dev` variant resolves the simulated gateway here exactly
 * as the `prod` variant resolves the real one.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BillingClientApiEntryPoint {
    fun billingGateway(): SubscriptionBillingGateway
}