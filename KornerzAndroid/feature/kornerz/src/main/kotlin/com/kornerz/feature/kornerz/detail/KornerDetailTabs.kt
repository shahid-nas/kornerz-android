package com.kornerz.feature.kornerz.detail

/**
 * The two tabs of the Korner detail "Nooks" section. Mirrors iOS
 * `KornerDetailNookType` (`SwiftUI/Dashboard/Korner/Sections/
 * KornerDetailNooksView.swift`).
 */
enum class KornerDetailNookTab(val title: String) {
    Current("Current"),
    Upcoming("Upcoming"),
}

/**
 * The two tabs of the Korner detail users section. Mirrors iOS
 * `KornerUsersTab` (`SwiftUI/Dashboard/Korner/Sections/KornerUsersView.swift`).
 */
enum class KornerDetailUserTab(val title: String) {
    Kornerstones("Kornerstones"),
    Klouders("Klouders"),
}
