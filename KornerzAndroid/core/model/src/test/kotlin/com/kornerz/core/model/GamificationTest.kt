package com.kornerz.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the `Gamification` model's wire mapping against iOS's
 * `CodingKeys` (`kornerz/Kornerz/Models/Gamification.swift:19-26`).
 *
 * The field names are the whole point: the backend writes
 * `gameficationQtdPts`, and the P10 leaderboard reads that exact
 * spelling. A rename here silently zeroes every score.
 */
class GamificationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes the iOS field names verbatim`() {
        val decoded = json.decodeFromString<Gamification>(
            """
            {
              "gameficationId": "gam-1",
              "gameficationAction": "like",
              "gameficationDateHora": "2026-10-01T10:15:30Z",
              "gameficationQtdPts": 25,
              "gameficationUserId": "user-1",
              "gameficationReferenceId": "ref-1"
            }
            """.trimIndent(),
        )

        assertEquals("gam-1", decoded.id)
        assertEquals("like", decoded.action)
        assertEquals(25, decoded.points)
        assertEquals("user-1", decoded.userId)
        assertEquals("ref-1", decoded.referenceId)
        assertEquals("2026-10-01T10:15:30Z", decoded.date?.toString())
    }

    @Test
    fun `points is read from gameficationQtdPts and not from gameficationPoints`() {
        val decoded = json.decodeFromString<Gamification>(
            """{"gameficationQtdPts": 7, "gameficationPoints": 999}""",
        )

        assertEquals(7, decoded.points)
    }

    @Test
    fun `a document without a points field decodes to null`() {
        val decoded = json.decodeFromString<Gamification>("""{"gameficationId": "gam-2"}""")

        assertNull(decoded.points)
    }

    @Test
    fun `unknown fields are tolerated`() {
        val decoded = json.decodeFromString<Gamification>(
            """{"gameficationId": "gam-3", "somethingNew": true}""",
        )

        assertEquals("gam-3", decoded.id)
    }
}
