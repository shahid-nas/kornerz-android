package com.kornerz.core.model

/**
 * Filter chips shown above the Loops tab feed. Mirrors the iOS
 * `LoopsFilterType` enum (`kornerz/SwiftUI/Dashboard/Loops/LoopsTab/LoopsFilterView.swift:10-13`).
 *
 * Each case carries the [displayName] used as the chip label, so the UI
 * layer does not need its own `when` block to translate enum → string.
 */
enum class LoopsFilter(val displayName: String) {
    ALL("All"),
    YOUR_LOOPS("Your Loops"),
}
