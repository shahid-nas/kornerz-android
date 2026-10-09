package com.kornerz.feature.kornerz.members

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kornerz.core.model.User
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.core.ui.theme.Spacing

/**
 * Members section of the Korner detail screen (spec
 * `korner-detail/spec.md` §4.10). Horizontal `LazyRow` of 40dp diameter
 * circular avatar tiles with a name label below.
 *
 * Section hidden when empty per spec §"Members is hidden when empty".
 */
@Composable
fun MembersSection(
    members: List<User>,
    onMemberClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = Spacing.space4)) {
        Text(
            text = "Members",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = Spacing.space8, vertical = Spacing.space2),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.space8),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
        ) {
            items(members, key = { it.id }) { user ->
                MemberTile(user = user, onClick = { onMemberClick(user.id) })
            }
        }
    }
}

@Composable
private fun MemberTile(
    user: User,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(56.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = user.photoUrl,
                contentDescription = user.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            if (user.photoUrl.isNullOrBlank()) {
                Text(
                    text = user.displayName.take(1).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text(
            text = user.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MembersSectionPreview() {
    KornerzTheme(darkTheme = false) {
        MembersSection(
            members = listOf(
                User(id = "u1", email = "a@a.com", displayName = "Ada", nickname = "ada"),
                User(id = "u2", email = "b@b.com", displayName = "Bob", nickname = "bob"),
            ),
            onMemberClick = {},
        )
    }
}
