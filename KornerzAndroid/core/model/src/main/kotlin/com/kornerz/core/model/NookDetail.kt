package com.kornerz.core.model

import kotlinx.serialization.Serializable

/**
 * Aggregate payload for the Nook detail screen. Mirrors the iOS
 * `NookCommunityWrapper` shape used in `NookDetailView.swift` — a
 * composite of the [Nook] (iOS `Event`) and the parent [Korner] (iOS
 * `Community`).
 *
 * `Nook` and `Korner` are the existing P3 models reused without
 * modification per spec `nook-domain/spec.md` §"NookDetail aggregate is
 * composed from Nook and Korner".
 */
@Serializable
data class NookDetail(
    val nook: Nook,
    val korner: Korner,
)
