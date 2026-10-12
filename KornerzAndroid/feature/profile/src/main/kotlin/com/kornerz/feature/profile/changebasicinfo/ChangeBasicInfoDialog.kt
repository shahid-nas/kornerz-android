package com.kornerz.feature.profile.changebasicinfo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kornerz.core.model.EditableField
import com.kornerz.core.ui.theme.KornerzColors
import com.kornerz.core.ui.theme.Satoshi

/**
 * Rounded 20-pt card with a single text field and a Save pill button
 * (P6, task 5.13). Mirrors iOS `ChangeBasicInfoVC` lines 105–116.
 *
 * Visual spec (per `profile-edit-basic-info-field` spec):
 * - Card: 20-pt corner radius
 * - Heading: Satoshi Black 22-pt, `SlateLight` (`C353446`)
 * - Text field: Satoshi Bold 26-pt, `InkLight` (`C1B1A23`)
 * - Save button:
 *   - enabled: `PurpleLight` (`C5D47ED`) bg, white text, Satoshi
 *     Bold 17-pt, **fully-rounded pill** (corner radius = height/2)
 *   - disabled: `SmokeLight` (`CF2F4F6`) bg, `PurpleGreyLight`
 *     (`C9E9EAE`) text, no ripple
 * - IME inset: `imePadding()` lifts the Save button above the
 *   soft keyboard (spec scenario, mirrors iOS
 *   `handle(keyboardShowNotification:)` lines 130–139).
 */
@Composable
fun ChangeBasicInfoDialog(
    state: ChangeBasicInfoState,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
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
                        text = headingFor(state.field),
                        color = KornerzColors.SlateLight,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = state.draftValue,
                        onValueChange = onDraftChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontFamily = Satoshi,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = KornerzColors.InkLight,
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = KornerzColors.InkLight,
                            unfocusedTextColor = KornerzColors.InkLight,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = KornerzColors.PurpleLight,
                            unfocusedIndicatorColor = KornerzColors.PurpleGreyLight,
                        ),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(20.dp))
                    SavePill(
                        enabled = state.canSave,
                        onClick = onSave,
                    )
                }
            }
        }
    }
}

@Composable
private fun SavePill(enabled: Boolean, onClick: () -> Unit) {
    val bg = if (enabled) KornerzColors.PurpleLight else KornerzColors.SmokeLight
    val fg = if (enabled) Color.White else KornerzColors.PurpleGreyLight
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = fg,
            disabledContainerColor = bg,
            disabledContentColor = fg,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Save",
            color = fg,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * The dialog heading copy. Mirrors iOS `ChangeBasicInfoVC.setUpValues`
 * (lines 73–89) — the iOS heading is the field name, not "Change X".
 */
fun headingFor(field: EditableField): String = when (field) {
    EditableField.NAME -> "Full Name"
    EditableField.NICKNAME -> "Nickname"
    EditableField.EMAIL -> "E-mail"
    EditableField.PHONE -> "Telefone Celular"
    EditableField.PASSWORD -> "Current Password"
}
