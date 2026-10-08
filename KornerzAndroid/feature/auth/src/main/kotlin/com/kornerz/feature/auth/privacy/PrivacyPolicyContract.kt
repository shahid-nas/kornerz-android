package com.kornerz.feature.auth.privacy

/**
 * UI state for the standalone Privacy Policy screen that gates entry
 * into the signup wizard. The "Accept" CTA is disabled until the
 * user has scrolled the policy text to its end at least once.
 */
data class PrivacyPolicyState(
    val hasScrolledToEnd: Boolean = false,
) {
    val canAccept: Boolean get() = hasScrolledToEnd
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