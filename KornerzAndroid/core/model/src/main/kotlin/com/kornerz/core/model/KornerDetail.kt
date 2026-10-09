package com.kornerz.core.model

import kotlinx.serialization.Serializable

/**
 * Aggregate payload for the Korner detail screen. Composed from three
 * Firestore reads by [com.kornerz.core.network.kornerz.KornerzRepository]
 * — `communities/{id}` (the [korner]), `communities/{id}/events` (the
 * [nooks] list), and `communities/{id}/members` (the [members] list).
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
