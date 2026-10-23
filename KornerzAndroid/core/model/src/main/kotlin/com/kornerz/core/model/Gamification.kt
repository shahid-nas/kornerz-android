package com.kornerz.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One `Gamefication/{id}` document — the gamification ledger entry
 * that backs the Klouders leaderboard and the tab header's points chip.
 *
 * Mirrors iOS `Models/Gamification.swift` (lines 19–26) field-for-field.
 * Every property carries the iOS `CodingKeys` spelling verbatim, which
 * is what makes the leaderboard's points field impossible to "correct"
 * back into a guess: the backend writes `gameficationQtdPts`, not
 * `gameficationPoints`.
 *
 * **Typo note:** the collection is spelled `Gamefication` (no `m`) on
 * the backend and in iOS (`GamificationService.swift:27`); the property
 * names carry the same misspelling. Both are preserved verbatim.
 *
 * The model is Compose-free by design — `core/model` is a plain Kotlin
 * library and must not depend on the UI toolkit.
 */
@Serializable
data class Gamification(
    @SerialName("gameficationId") val id: String? = null,
    @SerialName("gameficationAction") val action: String? = null,
    @SerialName("gameficationDateHora") val date: Instant? = null,
    @SerialName("gameficationQtdPts") val points: Int? = null,
    @SerialName("gameficationUserId") val userId: String? = null,
    @SerialName("gameficationReferenceId") val referenceId: String? = null,
)
