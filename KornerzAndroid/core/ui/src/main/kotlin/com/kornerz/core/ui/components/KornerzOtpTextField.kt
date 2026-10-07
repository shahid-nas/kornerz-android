package com.kornerz.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Mirrors iOS `AEOTPTextField` (architecture §5). Six single-digit boxes;
 * the user types the code sequentially and we surface the combined string
 * via [onValueChange]. The internal layout supports pasting a full code
 * (right-to-left fill).
 */
@Composable
fun KornerzOtpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    enabled: Boolean = true,
    isError: Boolean = false,
) {
    val digits = remember(value, length) {
        List(length) { idx -> value.getOrNull(idx)?.toString().orEmpty() }
    }
    var raw by remember { mutableStateOf(value) }

    BasicTextField(
        value = raw,
        onValueChange = { input ->
            val filtered = input.filter { it.isDigit() }.take(length)
            raw = filtered
            onValueChange(filtered)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = modifier,
        decorationBox = { _ ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                digits.forEachIndexed { index, digit ->
                    OtpCell(
                        value = digit,
                        enabled = enabled,
                        isError = isError,
                        modifier = Modifier.size(width = 44.dp, height = 56.dp),
                    )
                    if (index == length / 2 - 1) {
                        // Visual midpoint gap to mirror iOS pattern; no
                        // separator required at P1 — left here for future
                        // polish (architecture §5 documents the mirror).
                        @Suppress("UnusedExpression") Text("")
                    }
                }
            }
        },
    )
}

@Composable
private fun OtpCell(
    value: String,
    enabled: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}