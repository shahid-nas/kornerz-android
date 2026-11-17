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
                SubscriptionScreen(
                    onNavEvent = { event ->
                        when (event) {
                            is SubscriptionNavEvent.PurchaseSucceeded ->
                                scope.navController.navigate(SubscriptionRoute.Success.route) {
                                    launchSingleTop = true
                                }

                            // A restore recovered an existing purchase and
                            // the entitlement has been written. The paywall
                            // is mandatory — no close affordance, and the
                            // back gesture is swallowed — so this event is
                            // the *only* way off this route for a user who
                            // already paid. It unwinds past the plans
                            // route, exactly as the success route's
                            // dismissal does.
                            //
                            // Before this was handled, the restore wrote
                            // the entitlement and then did nothing
                            // visible: the user stayed on the paywall,
                            // which was re-presented on the next resume
                            // because the gate's "satisfied" latch was
                            // never set. That is what made the button
                            // look inert.
                            is SubscriptionNavEvent.Restored -> {
                                premiumGate.markSatisfied()
                                scope.onPremiumRouteClosed()
                                val unwound = scope.navController.popBackStack(
                                    SubscriptionRoute.Plans.route,
                                    inclusive = true,
                                )
                                if (!unwound) scope.navController.popBackStack()
                            }

                            // Reuses the existing P6 `profile/privacy-policy`
                            // destination rather than a subscription-local
                            // copy of the PDF viewer.
                            //
                            // The viewer is **pushed on top of** the plans
                            // route, which is what iOS does: iOS presents
                            // the paywall modally and pushes
                            // `PrivacyPolicyView` onto the *same*
                            // navigation controller
                            // (`SubscriptionController.swift:81-85`), so
                            // Back returns to the paywall.
                            //
                            // The plans route is deliberately **not**
                            // popped. It used to be: the pop was there
                            // because the paywall swallows the system
                            // back gesture, so the viewer could not be
                            // dismissed back into it. But popping removed
                            // the paywall from the back stack entirely,
                            // so Back from the viewer landed the user in
                            // the un-gated app — a premium-gate bypass — and
                            // the gate's clause-1 latch stayed stuck
                            // `true`, so the paywall was never re-armed.
                            //
                            // Pushing instead keeps the paywall on the
                            // stack: Back from the viewer returns to it,
                            // and the gate's latch stays truthful.
                            SubscriptionNavEvent.OpenPrivacyPolicy ->
                                scope.openPrivacyPolicy()

                            // iOS `SubscriptionViewModel.swift:152-157`
                            // opens the EULA externally. The back stack is
                            // deliberately untouched.
                            SubscriptionNavEvent.OpenTermsOfUse ->
                                scope.onOpenTermsOfUse(TERMS_OF_USE_URL)

                            // The plans screen renders the message inline;
                            // the event exists so the shell's `when` stays
                            // exhaustive.
                            is SubscriptionNavEvent.PurchaseFailed -> Unit

                            // The wrong-account sheet's "Log out" action.
                            //
                            // The sheet is raised when a restore fails
                            // because the purchase belongs to a different
                            // Kornerz account. That is a dead end: the
                            // paywall has no close affordance and swallows
                            // the back gesture, so without this the user is
                            // stuck until they delete the app.
                            //
                            // The teardown itself belongs to the shell —
                            // `feature/subscription` may not depend on
                            // `feature/shell` — so the request is handed
                            // over and `ShellViewModel.onLogout` clears the
                            // FCM token, clears the session, and navigates
                            // to the auth graph.
                            SubscriptionNavEvent.Logout -> scope.onLogout()
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

/** iOS `SubscriptionViewModel.swift:152-157`. */
private const val TERMS_OF_USE_URL =
    "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/"