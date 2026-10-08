package com.kornerz.feature.auth.privacy

import com.kornerz.feature.auth.BuildConfig

/**
 * UI state for the standalone Privacy Policy screen that gates entry
 * into the signup wizard. The "Accept" CTA is disabled until the
 * user has scrolled the policy text to its end at least once.
 *
 * In debug builds (any flavor — `assembleDevDebug`, `assembleProdDebug`,
 * etc.) the scroll-to-end gate is bypassed so QA / developers can
 * reach the signup wizard without having to scroll the bundled PDF
 * every launch. Release builds retain the production scroll-to-end
 * behavior defined in `specs/signup-wizard/spec.md`.
 */
data class PrivacyPolicyState(
    val hasScrolledToEnd: Boolean = false,
) {
    val canAccept: Boolean get() = BuildConfig.DEBUG || hasScrolledToEnd
}

sealed interface PrivacyPolicyEvent {
    data class ScrolledToEndChanged(val value: Boolean) : PrivacyPolicyEvent
    data object Accept : PrivacyPolicyEvent
    data object Decline : PrivacyPolicyEvent
}

sealed interface PrivacyPolicyNavEvent {
    data object NavigateToSignup : PrivacyPolicyNavEvent
    data object NavigateBack : PrivacyPolicyNavEvent
}