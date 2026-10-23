package com.kornerz.core.ui.components.nooks

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kornerz.core.model.Korner
import com.kornerz.core.model.Nook
import com.kornerz.core.model.User
import com.kornerz.core.ui.theme.KornerzTheme
import kotlinx.datetime.Instant

private val sampleKorner = Korner(
    id = "c1",
    name = "Design Korner",
    themeColor = "#E27D5A",
    membersCount = 42,
)

private val sampleNook = Nook(
    id = "n1",
    communityId = "c1",
    title = "Friday Fireside",
    startDate = Instant.parse("2026-10-10T19:00:00Z"),
    type = "open",
    publisherId = "p1",
)

private val samplePublisher = User(
    id = "p1",
    email = "p@example.com",
    displayName = "Ada",
    nickname = "ada",
    userCode = "p1",
)

@Preview(name = "LiveKornerCard — Light", showBackground = true)
@Composable
private fun LiveKornerCardLightPreview() {
    KornerzTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            LiveKornerCard(
                korner = sampleKorner,
                accentColor = nooksAccentColor(0),
                onClick = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(name = "LiveKornerCard — Dark", showBackground = true)
@Composable
private fun LiveKornerCardDarkPreview() {
    KornerzTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            LiveKornerCard(
                korner = sampleKorner,
                accentColor = nooksAccentColor(0),
                onClick = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(name = "ScheduledNookCard — Light", showBackground = true)
@Composable
private fun ScheduledNookCardLightPreview() {
    KornerzTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ScheduledNookCard(
                nook = sampleNook,
                publisher = samplePublisher,
                parentKornerName = "Design Korner",
                parentKornerCoverUrl = null,
                accentColor = nooksAccentColor(1),
                onClick = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(name = "ScheduledNookCard — Dark", showBackground = true)
@Composable
private fun ScheduledNookCardDarkPreview() {
    KornerzTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ScheduledNookCard(
                nook = sampleNook,
                publisher = samplePublisher,
                parentKornerName = "Design Korner",
                parentKornerCoverUrl = null,
                accentColor = nooksAccentColor(1),
                onClick = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
