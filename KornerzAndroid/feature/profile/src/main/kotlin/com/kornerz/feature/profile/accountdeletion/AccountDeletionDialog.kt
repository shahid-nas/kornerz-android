package com.kornerz.feature.profile.accountdeletion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kornerz.core.ui.components.KornerzLoadingView
import com.kornerz.core.ui.theme.KornerzColors

/**
 * Confirm dialog for the "Delete Account" flow. Mirrors iOS
 * `UserProfileVC.deleteAccount` lines 152–185 which uses
 * `showAlertControllerWith(message:alertTitle:cancelButtonTitle:okayButtonTitle:)`
 * (destructive action). The Android port uses a Material 3 `Dialog`
 * with a Cancel + Delete pair, plus a loading overlay while the
 * Cloud Function call is in flight.
 */
@Composable
fun AccountDeletionConfirmDialog(
    state: AccountDeletionState,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!state.showConfirmDialog) return
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                Text(
                    text = "Delete Account",
                    color = KornerzColors.SlateLight,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.error
                        ?: "Are you sure you want to delete your account? This action cannot be undone.",
                    color = KornerzColors.SlateLight,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(16.dp))
                if (state.isDeleting) {
                    KornerzLoadingView(modifier = Modifier.fillMaxWidth().height(48.dp))
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onCancel) {
                            Text(
                                "Cancel",
                                color = KornerzColors.SlateLight,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(0.dp))
                        Button(
                            onClick = onConfirm,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KornerzColors.RedLight,
                                contentColor = Color.White,
                            ),
                        ) {
                            Text(
                                "Delete",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
