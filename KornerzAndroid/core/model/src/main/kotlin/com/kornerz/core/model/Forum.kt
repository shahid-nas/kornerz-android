package com.kornerz.core.model

/**
 * Placeholder Forum type. The full `Forum` schema (posts, replies,
 * members, permissions) lands in a later phase. The Recent articles
 * sealed interface declares a `.forum` branch to keep the type
 * stable for the iOS 1:1 parity, but P2 does not produce any
 * `.forum` instances — see `home-recent-articles/spec.md`.
 */
data class Forum(
    val id: String,
    val title: String,
    val content: String,
    val publisherId: String? = null,
)
