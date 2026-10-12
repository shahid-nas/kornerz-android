package com.kornerz.feature.profile.editbasicinfo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.model.EditableField
import com.kornerz.core.model.User
import com.kornerz.core.ui.theme.KornerzColors

/**
 * A single row on the Edit Basic Info screen (P6, task 5.9).
 * Mirrors iOS `BasicProfileInfoCell`.
 *
 * Typography + colors (per `profile-edit-basic-info` spec):
 * - title: Satoshi Bold 16-pt, `SlateLight` (`C353446`)
 * - value: Satoshi Bold 14-pt, `PurpleGreyLight` (`C9E9EAE`)
 * - "Change" button: Satoshi Bold 16-pt, `PurpleLight` (`C5D47ED`)
 * - Password masked: `••••••••` (mirrors iOS line 59 `setValueAsMasked`)
 */
@Composable
fun BasicInfoRow(
    title: String,
    value: String,
    isChangeable: Boolean,
    isPassword: Boolean = false,
    onChange: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = Color(0xFFF2F4F6),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = isChangeable, onClick = onChange)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = KornerzColors.SlateLight,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.padding(top = 4.dp))
                Text(
                    text = if (isPassword) "••••••••" else value.ifEmpty { "—" },
                    color = KornerzColors.PurpleGreyLight,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (isChangeable) {
                TextButton(onClick = onChange) {
                    Text(
                        text = "Change",
                        color = KornerzColors.PurpleLight,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = KornerzColors.SlateLight,
                )
            }
        }
    }
}

/**
 * i18n lookup table for the Edit Basic Info row titles. Mirrors
 * iOS `EditBasicInfoVC.setupMenuList` (lines 55–80) — the iOS row
 * titles are the field name (e.g. "Full Name") NOT "Change X".
 * The corresponding `ChangeBasicInfoDialog` uses the same copy.
 */
@Composable
fun fieldLabel(field: EditableField): String = when (field) {
    EditableField.NAME -> "Full Name"
    EditableField.EMAIL -> "E-mail"
    EditableField.PHONE -> "Telefone Celular"
    EditableField.NICKNAME -> "Nickname"
    EditableField.PASSWORD -> "Current Password"
}

fun valueForField(user: User, field: EditableField): String = when (field) {
    EditableField.NAME -> user.displayName
    EditableField.EMAIL -> user.email
    EditableField.PHONE -> user.phone.orEmpty()
    EditableField.NICKNAME -> user.nickname
    EditableField.PASSWORD -> ""
}
