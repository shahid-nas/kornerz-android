package com.kornerz.feature.subscription.plans.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.kornerz.core.ui.components.KornerzCapsuleButton
import com.kornerz.core.ui.components.KornerzCapsuleButtonStyle
import com.kornerz.core.ui.theme.KornerzColors
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.KornerzTypography
import com.kornerz.core.ui.theme.Spacing

/**
 * The sheet shown when a restore fails with
 * `RestoreOutcome.WrongAccount` — the device's Play account holds a
 * Kornerz subscription, but it is tagged to a different Kornerz account
 * than the one signed in here.
 *
 * **Why a sheet and not an inline message.** The inline form left the
 * user on a paywall they cannot pass: the Subscribe button fails with
 * `ITEM_ALREADY_OWNED` and Restore fails with the same account mismatch,
 * so the only way forward was to sign out — but nothing on the screen
 * said so, and the paywall has no close affordance and swallows the back
 * gesture. The sheet names the problem and offers the one action that
 * actually resolves it.
 *
 * **Why "Log out" is the primary action.** The subscription follows the
 * **Google** account, not the app account, and Play Billing never
 * exposes the Play account's identity to the app — so the app cannot
 * tell the user which Google account to switch to. Signing out and back
 * in as the account that made the purchase is the only actionable step
 * the app can offer, and it is the same step iOS takes when the
 * subscription cannot be restored.
 *
 * The sheet is not dismissible by tapping outside: cancelling leaves the
 * user in exactly the state they were in, so the sheet offers an explicit
 * "Cancel" instead of a tap-out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WrongAccountSheet(
    onLogoutClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onCancelClick,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.space8)
                .padding(bottom = Spacing.space24),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Text(
                text = TITLE,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontFamily = KornerzTypography.headlineSmall.fontFamily,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(Spacing.space8))

            Text(
                text = MESSAGE,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                fontFamily = KornerzTypography.bodyMedium.fontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(Spacing.space16))

            KornerzCapsuleButton(
                text = LOGOUT,
                onClick = onLogoutClick,
                style = KornerzCapsuleButtonStyle.BlackOnWhite,
                containerColor = KornerzColors.RedLight,
                contentColor = Color.White,
            )

            Spacer(modifier = Modifier.height(Spacing.space4))

            TextButton(onClick = onCancelClick) {
                Text(
                    text = CANCEL,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontFamily = KornerzTypography.bodyMedium.fontFamily,
                )
            }
        }
    }
}

private const val TITLE = "Subscription on another account"

private const val MESSAGE =
    "This subscription was made with a different Google account. " +
        "Sign out and sign in with the account that made the purchase " +
        "to restore it."

private const val LOGOUT = "Log out"

private const val CANCEL = "Cancel"

@Preview(showBackground = true, name = "Wrong account sheet")
@Composable
private fun WrongAccountSheetPreview() {
    KornerzTheme(darkTheme = false) {
        WrongAccountSheet(onLogoutClick = {}, onCancelClick = {})
    }
}
