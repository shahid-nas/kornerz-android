package com.kornerz.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Bridges the shell's `ON_RESUME` lifecycle event to the premium gate.
 *
 * The `premium-gate` spec names **two** gate call sites: post-login and
 * shell resume. The post-login site is `ShellViewModel.init` (the shell
 * graph being composed *is* the handoff from login). The resume site is
 * this composable.
 *
 * iOS's counterpart is `AppTabController`'s Combine sink
 * (`kornerz/Kornerz/SwiftUI/TabView/AppTabController.swift:170-189`):
 *
 * ```swift
 * Publishers.CombineLatest(
 *     InAppManager.shared.purchasedProducts.dropFirst(),
 *     AppHelper.shared.userPublisher.compactMap { $0 }
 * )
 * .receive(on: DispatchQueue.main)
 * .sink { [weak self] products, user in
 *     self?.showSubscriptionScreen()
 *     ...
 * }
 * ```
 *
 * That sink re-fires whenever StoreKit's entitlement list changes **or**
 * the signed-in user changes, and re-evaluates the gate each time. The
 * Android equivalent of "the entitlement list changed" is the app coming
 * back to the foreground: a subscription may have been bought or renewed
 * on the Play Store while the app was backgrounded, and only a fresh
 * reconciliation can discover it. `onShellResumed` performs exactly that
 * reconciliation before evaluating the gate
 * (`ShellViewModel.requestPremiumGateEvaluation`).
 *
 * The observer is attached with [DisposableEffect] so it is removed with
 * the composition, and it ignores every event other than `ON_RESUME` —
 * notably it does **not** fire on tab switches or destination changes,
 * which would slam the paywall back on a free user every time they moved
 * between tabs.
 */
@Composable
fun ObserveShellResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
