package com.kornerz.feature.community.admin.createforum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.ui.theme.Satoshi
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.community.R

/**
 * Step 1 of the create-forum wizard — the loop title.
 *
 * 1:1 port of iOS `AddForumTitleScreen`
 * (`kornerz/Kornerz/HomeVC/AddForum/AddForumTitleScreen.swift`).
 *
 * iOS renders a `List` whose only row holds the bold section heading
 * followed by a **borderless** `TextEditor` at 24 sp Satoshi Bold. When
 * the editor is empty, a grey overlay shows the same "Loop Title" copy
 * as a placeholder. A list footer shows the character count once the
 * field is non-empty, and the circular next arrow floats in the
 * bottom-trailing corner.
 *
 * The Android port keeps the borderless editor (a `BasicTextField`
 * with a transparent background rather than a Material
 * `OutlinedTextField`), the same placeholder overlay, the same footer
 * counter, and the same auto-focus on appear (`isTextFieldFocus.toggle()`
 * in iOS's `onAppear`).
 */
@Composable
fun CreateForumTitleStep(
    title: String,
    onTitleChange: (String) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val heading = stringResource(R.string.create_forum_loop_title_label)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.space12),
    ) {
        Spacer(modifier = Modifier.height(Spacing.space8))
        CreateForumSectionHeading(text = heading)

        Spacer(modifier = Modifier.height(Spacing.space4))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.space4),
        ) {
            if (title.isEmpty()) {
                Text(
                    text = heading,
                    fontFamily = Satoshi,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            BasicTextField(
                value = title,
                onValueChange = onTitleChange,
                textStyle = TextStyle(
                    fontFamily = Satoshi,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = Color(0xFF1B1A23),
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }

        if (title.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.space4))
            CreateForumCounter(
                text = stringResource(
                    R.string.create_forum_title_counter,
                    title.length,
                    TITLE_MAX_LENGTH,
                ),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.space12))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            CreateForumNextButton(
                enabled = title.isNotEmpty(),
                onClick = onNext,
            )
        }
    }
}

/** iOS `AddForumViewModel.title` caps the title at 100 characters. */
private const val TITLE_MAX_LENGTH = 100
