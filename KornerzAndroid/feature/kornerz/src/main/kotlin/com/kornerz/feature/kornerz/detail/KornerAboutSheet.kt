package com.kornerz.feature.kornerz.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.kornerz.core.model.Korner
import com.kornerz.core.ui.components.KornerzCapsuleButton
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing

/**
 * The Korner's About sheet. 1:1 port of iOS `KornerAboutView`
 * (`SwiftUI/Dashboard/Korner/KornerAbout/KornerAboutView.swift`),
 * which iOS presents modally from the detail header's "About ⌄"
 * affordance (`KornerDetailCoordinator.openAbout` — a
 * `sheetPresentationController` with `.medium()` / `.large()`
 * detents). Android's `ModalBottomSheet` is the same surface.
 *
 * The sheet carries the community name, a "Description" label, the
 * description itself, and the Follow / Unfollow primary action
 * pinned to the bottom (iOS `.safeAreaInset(edge: .bottom)`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KornerAboutSheet(
    korner: Korner,
    isFollowing: Boolean,
    onFollowClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.space8)
                .padding(bottom = Spacing.space24),
        ) {
            Text(
                text = korner.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(Spacing.space12))
            Text(
                text = "Description",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
                // iOS `AppColor.C9E9EAE` — the muted grey the label uses.
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Spacing.space4))
            Text(
                text = korner.description.orEmpty(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(Spacing.space16))
            KornerzCapsuleButton(
                text = if (isFollowing) "Unfollow" else "Follow",
                onClick = onFollowClick,
                // iOS `.capsuleStyle(.primary)` — the brand purple with
                // a white label.
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            )
        }
    }
}

@Preview(showBackground = true, name = "Korner about sheet")
@Composable
private fun KornerAboutSheetPreview() {
    KornerzTheme(darkTheme = false) {
        KornerAboutSheet(
            korner = Korner(
                id = "c1",
                name = "Design Korner",
                themeColor = "#E27D5A",
                description = "A place to talk design, share work and run weekly fireside chats.",
            ),
            isFollowing = false,
            onFollowClick = {},
            onDismiss = {},
        )
    }
}
