package com.kornerz.kornerz

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.kornerz.feature.shell.PremiumGate
import com.kornerz.feature.shell.ShellExtraRoute
import com.kornerz.feature.subscription.navigation.SubscriptionRoute
import com.kornerz.feature.subscription.plans.SubscriptionNavEvent
import com.kornerz.feature.subscription.plans.SubscriptionScreen
import com.kornerz.feature.subscription.success.SubscriptionSuccessNavEvent
import com.kornerz.feature.subscription.success.SubscriptionSuccessScreen
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Grants [AppRoot] access to the `@Singleton` [PremiumGate], which lives in
 * `feature/shell`. Resolved through a local `@EntryPoint` rather than
 * threading it down as a parameter, so `AppRoot`'s signature stays about
 * the navigation graph rather than about the paywall.
 *
 * Installed in [SingletonComponent] because `AppRoot` resolves it with
 * `EntryPointAccessors.fromApplication`, which can only see entry points in
 * the application (singleton) component. Installing it in
 * `ActivityComponent` compiles and links fine but throws
 * `ClassCastException` at launch, because the singleton implementation
 * does not implement an activity-scoped entry point.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PremiumGateEntryPoint {
    fun premiumGate(): PremiumGate
}

/**
 * Registers the two P8 subscription routes on the shell's root `NavHost`.
 *
 * The wiring lives in `app` rather than in `AppShell` because
 * `docs/architecture.md` §2.5 makes `app` the only module allowed to
 * import both `feature/shell` and `feature/subscription`. `AppShell`
 * contributes the generic `ShellExtraRoute` slot; this fills it with the
 * concrete routes and owns their navigation effects.
 *
 * Both routes sit outside every tab's nested `NavHost` and are matched by
 * `AppShell`'s bottom-bar-hidden regex, so they render full-screen behind
 * the status bar.
 *
 * The paywall is mandatory: the plans route renders no close affordance and
 * swallows the system back gesture, so the only path off these two routes
 * is a completed purchase. The success route therefore unwinds **past** the
 * plans route — see [SubscriptionSuccessNavEvent.Dismissed] below.
 */
@Composable
fun rememberSubscriptionRoutes(premiumGate: PremiumGate): List<ShellExtraRoute> = remember(premiumGate) {
    listOf(
        ShellExtraRoute(
            route = SubscriptionRoute.Plans.route,
            content = { scope ->
                // `remember` is scoped to this back-stack entry, so the
                // pre-gate destination is captured once per presentation
                // and the unwind is deterministic rather than inferred from
                // the back stack's depth.
                val preGateRoute = androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf<String?>(null)
                }

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    preGateRoute.value = scope.navController.previousBackStackEntry
                        ?.destination
                        ?.route
                }

                SubscriptionScreen(
                    onNavEvent = { event ->
                        when (event) {
                            is SubscriptionNavEvent.PurchaseSucceeded ->
                                scope.navController.navigate(SubscriptionRoute.Success.route) {
                                    launchSingleTop = true
                                }

                            // Reuses the existing P6 `profile/privacy-policy`
                            // destination rather than a subscription-local
                            // copy of the PDF viewer.
                            SubscriptionNavEvent.OpenPrivacyPolicy -> {
                                popToPreGateDestination(
                                    scope.navController,
                                    preGateRoute.value,
                                )
                                scope.openPrivacyPolicy()
                            }

                            // iOS `SubscriptionViewModel.swift:152-157`
                            // opens the EULA externally. The back stack is
                            // deliberately untouched.
                            SubscriptionNavEvent.OpenTermsOfUse ->
                                scope.onOpenTermsOfUse(TERMS_OF_USE_URL)

                            // The plans screen renders these inline; they
                            // exist in the event set so the shell's
                            // `when` stays exhaustive.
                            is SubscriptionNavEvent.PurchaseFailed,
                            is SubscriptionNavEvent.Restored,
                            -> Unit
                        }
                    },
                )
            },
        ),
        ShellExtraRoute(
            route = SubscriptionRoute.Success.route,
            content = { scope ->
                SubscriptionSuccessScreen(
                    onNavEvent = { event ->
                        when (event) {
                            SubscriptionSuccessNavEvent.Dismissed -> {
                                // Purchase completed: mark satisfied and
                                // clear the dismissal latch, so the user is
                                // never re-gated for the rest of the
                                // process.
                                premiumGate.markSatisfied()
                                scope.onPremiumRouteClosed()
                                // Unwind **past** the plans screen, not just
                                // off the success screen. A plain
                                // `popBackStack()` pops one entry and lands
                                // the user back on `subscription/plans` —
                                // the paywall they just paid to leave. The
                                // plans route is popped inclusively instead,
                                // which removes both it and the success route
                                // and returns the user to whatever the gate
                                // interrupted (the bottom-bar tab screen).
                                val unwound = scope.navController.popBackStack(
                                    SubscriptionRoute.Plans.route,
                                    inclusive = true,
                                )
                                if (!unwound) scope.navController.popBackStack()
                            }
                        }
                    },
                )
            },
        ),
    )
}

/**
 * Returns to the destination that was current before the paywall opened,
 * falling back to a plain pop when that entry is gone (for example after a
 * process restore).
 *
 * Only the Privacy Policy link still needs this: the PDF viewer lives on the
 * Profile tab's nested `NavHost`, which is not composed while
 * `subscription/plans` is the current destination, so the plans route must
 * be popped for the viewer to render at all.
 */
private fun popToPreGateDestination(
    navController: androidx.navigation.NavHostController,
    preGateRoute: String?,
) {
    val popped = preGateRoute != null &&
        navController.popBackStack(preGateRoute, inclusive = false)
    if (!popped) navController.popBackStack()
}

/** iOS `SubscriptionViewModel.swift:152-157`. */
private const val TERMS_OF_USE_URL =
    "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/"