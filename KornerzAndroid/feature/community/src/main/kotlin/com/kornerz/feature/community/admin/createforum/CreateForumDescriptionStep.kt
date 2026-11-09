package com.kornerz.feature.community.admin.createforum

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.ui.theme.Satoshi
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.community.R

/**
 * The rich-text styles the description editor can apply to the
 * selected range.
 *
 * iOS drives its formatting toolbar from RichTextKit's
 * `RichTextContext`; Android has no equivalent, so the active styles
 * are tracked here and applied to the editor's selection.
 */
data class RichTextFormat(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
) {
    /** The [SpanStyle] this format applies to a range. */
    fun toSpanStyle(): SpanStyle = SpanStyle(
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        textDecoration = if (underline) TextDecoration.Underline else null,
    )
}

/**
 * Step 2 of the create-forum wizard — the loop description.
 *
 * 1:1 port of iOS `AddForumDescriptionScreen`
 * (`kornerz/Kornerz/HomeVC/AddForum/AddForumDescriptionScreen.swift`).
 *
 * iOS renders a `ScrollView` holding the bold "Loop Description"
 * heading with a `{count}/5000` counter on the right, followed by a
 * full-height rich-text editor with a 25 pt content inset and a grey
 * placeholder. The next arrow lives in the **top bar's trailing slot**
 * rather than the bottom corner, and the leading back button clears
 * the description before stepping back.
 *
 * The Android port keeps the heading + counter, the bordered editor
 * with its placeholder, the inline formatting toolbar, and the
 * trailing next arrow in the top bar.
 */
@Composable
fun CreateForumDescriptionStep(
    description: String,
    onDescriptionChange: (String) -> Unit,
    onDescriptionHtmlChange: (String) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val heading = stringResource(R.string.create_forum_loop_description_label)
    var fieldValue by remember { mutableStateOf(TextFieldValue(description)) }
    var format by remember { mutableStateOf(RichTextFormat()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.space12),
    ) {
        Spacer(modifier = Modifier.height(Spacing.space8))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CreateForumSectionHeading(text = heading)
            Spacer(modifier = Modifier.weight(1f))
            CreateForumCounter(
                text = stringResource(
                    R.string.create_forum_description_counter,
                    description.length,
                    DESCRIPTION_MAX_LENGTH,
                ),
                fontSize = 14,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.space4))
        RichTextEditor(
            value = fieldValue,
            onValueChange = { newValue ->
                fieldValue = newValue
                onDescriptionChange(newValue.text)
                onDescriptionHtmlChange(newValue.annotatedString.toDescriptionHtml())
            },
            placeholder = heading,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        )

        Spacer(modifier = Modifier.height(Spacing.space4))
        RichTextFormatToolbar(
            format = format,
            onToggleBold = {
                val next = format.copy(bold = !format.bold)
                format = next
                fieldValue = applyFormat(fieldValue, next)
            },
            onToggleItalic = {
                val next = format.copy(italic = !format.italic)
                format = next
                fieldValue = applyFormat(fieldValue, next)
            },
            onToggleUnderline = {
                val next = format.copy(underline = !format.underline)
                format = next
                fieldValue = applyFormat(fieldValue, next)
            },
        )

        Spacer(modifier = Modifier.height(Spacing.space12))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            CreateForumNextButton(
                enabled = description.isNotEmpty(),
                onClick = onNext,
            )
        }
    }
}

/**
 * Applies the active [RichTextFormat] to the editor's selected range.
 *
 * An empty selection (a collapsed cursor) is widened by one character
 * so a toggle still has something to style. The result is returned
 * rather than mutated in place, because the editor state is a hoisted
 * [TextFieldValue].
 */
private fun applyFormat(value: TextFieldValue, format: RichTextFormat): TextFieldValue {
    val selection = value.selection
    if (value.text.isEmpty()) return value
    val start = selection.min
    val end = if (selection.collapsed) {
        (selection.max + 1).coerceAtMost(value.text.length)
    } else {
        selection.max
    }
    if (start >= end) return value

    val style = format.toSpanStyle()
    val annotated = value.annotatedString
    val builder = AnnotatedString.Builder(annotated)
    builder.addStyle(style, start, end)
    return TextFieldValue(
        annotatedString = builder.toAnnotatedString(),
        selection = selection,
    )
}

/**
 * The rich-text editor.
 *
 * iOS hosts RichTextKit's `RichTextEditor`, which applies a 25 pt
 * content inset (`RichTextEditorView.swift:34`) and draws a grey
 * placeholder when empty (`:44-52`). Android's `BasicTextField` gives
 * the same behaviours with the same geometry.
 */
@Composable
private fun RichTextEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(RichTextContentPadding),
    ) {
        if (value.text.isEmpty()) {
            Text(
                text = placeholder,
                fontFamily = Satoshi,
                fontSize = 18.sp,
                color = Color.Gray,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontFamily = Satoshi,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * The formatting toolbar that sits under the editor.
 *
 * iOS renders RichTextKit's `RichTextKeyboardToolbar` pinned above the
 * keyboard, which offers the same bold / italic / underline controls
 * plus colour pickers. Android offers the three style toggles.
 */
@Composable
private fun RichTextFormatToolbar(
    format: RichTextFormat,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onToggleUnderline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FormatToggle(
            icon = { tint ->
                Icon(
                    imageVector = Icons.Filled.FormatBold,
                    contentDescription = "Bold",
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            },
            active = format.bold,
            onClick = onToggleBold,
        )
        FormatToggle(
            icon = { tint ->
                Icon(
                    imageVector = Icons.Filled.FormatItalic,
                    contentDescription = "Italic",
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            },
            active = format.italic,
            onClick = onToggleItalic,
        )
        FormatToggle(
            icon = { tint ->
                Icon(
                    imageVector = Icons.Filled.FormatUnderlined,
                    contentDescription = "Underline",
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            },
            active = format.underline,
            onClick = onToggleUnderline,
        )
    }
}

/**
 * One toolbar toggle. An active toggle is painted with the brand
 * purple, an inactive one with the outline colour.
 */
@Composable
private fun FormatToggle(
    icon: @Composable (Color) -> Unit,
    active: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(
                color = if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick) {
            icon(tint)
        }
    }
}

/** iOS `AddForumViewModel.description` caps the description at 5000 characters. */
private const val DESCRIPTION_MAX_LENGTH = 5000
