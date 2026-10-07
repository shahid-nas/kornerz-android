package com.kornerz.core.model

import kotlinx.serialization.Serializable

/**
 * One row in the bundled Korner-interest catalog used by the signup
 * wizard's interests step. `slug` is the stable identifier persisted on
 * the user document; `title` is the English label shown to the user.
 */
@Serializable
data class KornerInterest(
    val slug: String,
    val title: String,
)

/** Wrapper for the bundled catalog file (`korner_interests.json`). */
@Serializable
data class KornerInterests(
    val interests: List<KornerInterest>,
)