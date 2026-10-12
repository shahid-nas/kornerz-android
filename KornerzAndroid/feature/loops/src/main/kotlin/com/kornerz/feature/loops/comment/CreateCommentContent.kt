package com.kornerz.feature.loops.comment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kornerz.core.ui.components.KornerzNetworkAvatar
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing

@Composable
fun CreateCommentContent(
    state: CreateCommentState,
    onEvent: (CreateCommentEvent) -> Unit,
) {
    Dialog(onDismissRequest = { onEvent(CreateCommentEvent.CancelTapped) }) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(Spacing.space8)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KornerzNetworkAvatar(imageUrl = null, size = 32.dp)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "New comment",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.text,
                    onValueChange = { onEvent(CreateCommentEvent.TextChanged(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    placeholder = { Text("Write a comment…") },
                    enabled = !state.isSubmitting,
                )
                state.error?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { onEvent(CreateCommentEvent.CancelTapped) }) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = { onEvent(CreateCommentEvent.SendTapped) },
                        enabled = state.text.isNotBlank() && !state.isSubmitting,
                    ) {
                        Text(if (state.isSubmitting) "Sending…" else "Send")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, locale = "en")
@Composable
private fun CreateCommentContentPreview() {
    KornerzTheme(darkTheme = false) {
        Box {
            CreateCommentContent(
                state = CreateCommentState(text = "Hello world!"),
                onEvent = {},
            )
        }
    }
}

@Composable
private fun Box(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        content()
    }
}
