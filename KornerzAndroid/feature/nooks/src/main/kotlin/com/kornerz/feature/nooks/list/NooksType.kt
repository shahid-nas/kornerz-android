package com.kornerz.feature.nooks.list

/**
 * Live / Upcoming tab filter for the Nooks screen. Mirrors iOS
 * `NooksType` (`NooksTabViewModel.swift:26`). The selected type
 * drives which section is rendered by [NooksContent] (matches iOS
 * `NooksTabView.body` `if viewModel.nooksType == .live`).
 */
enum class NooksType(val title: String) {
    Live("Live"),
    Upcoming("Upcoming"),
}
