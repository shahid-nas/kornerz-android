package com.kornerz.feature.subscription.success

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * The success-screen host. Mirrors iOS `SubscriptionController`'s success
 * navigation (`:68-74`): the shell pushes this route after
 * `PurchaseSucceeded` and unwinds on `Dismissed`.
 */
@Composable
fun SubscriptionSuccessScreen(
    onNavEvent: (SubscriptionSuccessNavEvent) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SubscriptionSuccessViewModel = hiltViewModel(),
) {
    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect(onNavEvent)
    }

    // "Done" is the only exit (iOS hides the back button at `:37`), so the
    // system back gesture is captured rather than allowed to pop the route
    // behind the shell's own nav handling.
    BackHandler { viewModel.onDoneTap() }

    SubscriptionSuccessContent(
        onDoneTap = viewModel::onDoneTap,
        modifier = modifier,
    )
}
