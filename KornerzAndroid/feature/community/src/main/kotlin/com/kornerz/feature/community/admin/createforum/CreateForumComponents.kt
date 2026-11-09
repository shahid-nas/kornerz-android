package com.kornerz.feature.community.admin.createforum

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.ui.theme.Satoshi
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.community.R

/**
 * Shared chrome for the create-forum wizard: the top bar, the section
 * heading, and the circular "next" affordance.
 *
 * Every piece here has an iOS counterpart in
 * `kornerz/Kornerz/HomeVC/AddForum/AddForum*Screen.swift`, and the
 * Android versions reproduce the same geometry and copy.
 */

/**
 * The wizard's top bar.
 *
 * iOS shows a navigation title plus a leading back button and a
 * trailing close ("X") button on every screen of the flow
 * (`AddForumTitleScreen.swift:83-100` and siblings). The back button
 * pops one step, the close button abandons the whole wizard.
 *
 * The publish step's title is "Publish" rather than "Create Loop",
 * matching iOS's `.navigationTitle(getLocalizedValueFor("publish"))`
 * on `AddForumPublishScreen`.
 */
@Composable
fun CreateForumTopBar(
    title: String,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Spacing.space8, vertical = Spacing.space6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_create_forum_back),
                contentDescription = stringResource(R.string.create_forum_cd_back),
                tint = Color(0xFF353446),
            )
        }
        Text(
            text = title,
            fontFamily = Satoshi,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color(0xFF353446),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose) {
            Icon(
                painter = painterResource(R.drawable.ic_create_forum_close),
                contentDescription = stringResource(R.string.create_forum_cd_close),
                tint = Color(0xFF353446),
            )
        }
    }
}

/**
 * The bold section heading that opens each wizard step.
 *
 * iOS renders these with `Font(AppFonts.getSatoshiBlack(20.0))` and
 * `AppColor.C353446` (`AddForumTitleScreen.swift:22-27`,
 * `AddForumDescriptionScreen.swift:102-105`,
 * `AddForumCoverImageScreen.swift:27-32`,
 * `AddForumVideoScreen.swift:42-47`).
 */
@Composable
fun CreateForumSectionHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontFamily = Satoshi,
        fontWeight = FontWeight.Black,
        fontSize = 20.sp,
        color = Color(0xFF353446),
        modifier = modifier,
    )
}

/**
 * The circular "next" arrow that advances the wizard.
 *
 * iOS uses a 50x50 image in the bottom-trailing corner of the title,
 * cover-image, and video screens, and swaps between the `nextactive`
 * and `nextinactive` artwork depending on whether the step's input is
 * valid (`AddForumTitleScreen.swift:71-81`,
 * `AddForumCoverImageScreen.swift:107-118`,
 * `AddForumVideoScreen.swift:146-157`).
 *
 * The Android artwork is a vector port of those two PNGs, so the
 * button stays crisp at every density.
 */
@Composable
fun CreateForumNextButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(50.dp)
            .clip(RoundedCornerShape(percent = 50))
            .then(
                if (enabled) {
                    Modifier.clickableNoRipple(onClick)
                } else {
                    Modifier
                }
            ),
    ) {
        Image(
            painter = painterResource(
                if (enabled) {
                    R.drawable.ic_create_forum_next_active
                } else {
                    R.drawable.ic_create_forum_next_inactive
                }
            ),
            contentDescription = stringResource(R.string.create_forum_cd_next),
            modifier = Modifier.size(50.dp),
        )
    }
}

/**
 * The "skip" text button shown bottom-leading on the video step.
 *
 * iOS renders it as a bare `Text` inside a `NavigationLink`
 * (`AddForumVideoScreen.swift:137-144`), so the Android version is a
 * plain label rather than a Material button.
 */
@Composable
fun CreateForumSkipButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.create_forum_skip),
        fontFamily = Satoshi,
        fontSize = 16.sp,
        color = Color(0xFF353446),
        modifier = modifier.clickableNoRipple(onClick),
    )
}

/**
 * The grey placeholder character counter.
 *
 * iOS renders it as `.font(.system(size: 12)).foregroundColor(.gray)`
 * inside a `List` footer (`AddForumTitleScreen.swift:44-50`) and as
 * `.font(.system(size: 14)).foregroundColor(.gray)` in the description
 * header (`AddForumDescriptionScreen.swift:109-111`).
 */
@Composable
fun CreateForumCounter(
    text: String,
    fontSize: Int = 12,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontFamily = Satoshi,
        fontSize = fontSize.sp,
        color = Color.Gray,
        modifier = modifier,
    )
}

/**
 * A click handler without the ripple indication, used by the image
 * assets that carry their own pressed state (the circular next arrow
 * and the skip label).
 */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        onClick = onClick,
    )

/**
 * The content inset the iOS rich-text editor applies to its text
 * (`RichTextEditorView.swift:34` — `textContentInset = CGSize(width: 25, height: 25)`).
 */
internal val RichTextContentPadding = 25.dp
