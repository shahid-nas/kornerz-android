package com.kornerz.feature.nooks.list

/**
 * Live / Upcoming tab filter for the Nooks screen. Mirrors iOS
 * `NooksType` (`NooksTabViewModel.swift:26`). The selected type
 * drives which section is rendered by [NooksContent] (matches iOS
 * `NooksTabView.body` `if viewModel.nooksType == .live`).
 */
enum class NooksType(val title: String) {
    Live("Live"),
    Upcoming("Upcoming");

    /**
     * Mirrors iOS `nooksType.titleNooks` (`NooksTabViewModel.swift:37`).
     * Returns the title the header shows next to the leading icon —
     * "Live Nooks" / "Upcoming Nooks" — so the section's name updates
     * as the user toggles between tabs (matches iOS
     * `TabHeaderView(title: viewModel.nooksType.titleNooks, ...)`).
     */
    val titleNooks: String
        get() = when (this) {
            Live -> "Live Nooks"
            Upcoming -> "Upcoming Nooks"
        }
}
