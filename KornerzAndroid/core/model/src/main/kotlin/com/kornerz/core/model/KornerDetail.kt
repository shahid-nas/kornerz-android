package com.kornerz.core.model

import kotlinx.serialization.Serializable

/**
 * Aggregate payload for the Korner detail screen. Composed from three
 * Firestore reads by [com.kornerz.core.network.kornerz.KornerzRepository]
 * — the `Communities` document (the [korner]), the top-level `Events`
 * collection (the [nooks] list), and the `Fellow` + `Users`
 * collections (the [members] list).
 *
 * `User` is the existing P1 model, reused without modification per spec
 * `korner-domain/spec.md` §"User model from P1 is reused for members".
 */
@Serializable
data class KornerDetail(
    val korner: Korner,
    val nooks: List<Nook>,
    val members: List<User>,
)
