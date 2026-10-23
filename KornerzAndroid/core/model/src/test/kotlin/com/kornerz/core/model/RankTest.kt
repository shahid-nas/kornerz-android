package com.kornerz.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the [Rank] vocabulary and the fact that it replaces the nested
 * `UserRanking.Medal` enum.
 */
class RankTest {

    @Test
    fun `the rank vocabulary is exactly gold silver bronze none`() {
        assertEquals(
            listOf("GOLD", "SILVER", "BRONZE", "NONE"),
            Rank.entries.map { it.name },
        )
    }

    @Test
    fun `UserRanking defaults to no medal`() {
        assertEquals(Rank.NONE, UserRanking(userId = "u1").medal)
    }

    @Test
    fun `UserRanking keeps the madal wire name`() {
        // The backend's spelling is `madal` (one d) — iOS
        // `Models/UserRanking.swift` and the Android model agree on it.
        // The wire name is pinned by decoding, which is what actually
        // matters: the backend's spelling has to reach the model.
        val json = Json { ignoreUnknownKeys = true }

        val decoded = json.decodeFromString<UserRanking>(
            """{"userId":"u1","userName":"Ana","points":10,"madal":"GOLD"}""",
        )
        assertEquals(Rank.GOLD, decoded.medal)

        // The corrected spelling is **not** the wire name.
        val notDecoded = json.decodeFromString<UserRanking>(
            """{"userId":"u1","userName":"Ana","points":10,"medal":"GOLD"}""",
        )
        assertEquals(Rank.NONE, notDecoded.medal)
    }

    @Test
    fun `UserRanking keeps userName and points`() {
        val ranking = UserRanking(
            userId = "u2",
            userName = "Ana",
            points = 320,
            medal = Rank.GOLD,
        )

        assertEquals("u2", ranking.userId)
        assertEquals("Ana", ranking.userName)
        assertEquals(320, ranking.points)
        assertEquals(Rank.GOLD, ranking.medal)
    }
}
