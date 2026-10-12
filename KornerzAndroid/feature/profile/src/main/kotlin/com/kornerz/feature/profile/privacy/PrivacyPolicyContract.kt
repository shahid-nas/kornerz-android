package com.kornerz.feature.profile.privacy

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

/**
 * UI state for the Privacy Policy viewer (P6, screen 7.5). The accept
 * button is hidden when [acceptButtonVisible] is `false` (when the
 * destination is reached from the Profile menu).
 */
data class PrivacyPolicyState(
    val isFromProfile: Boolean = true,
    val acceptButtonVisible: Boolean = false,
    val assetPath: String = "privacy/privacy_en.pdf",
)

sealed interface PrivacyPolicyEvent {
    data object Accept : PrivacyPolicyEvent
    data object Back : PrivacyPolicyEvent
}

sealed interface PrivacyPolicyNavEvent {
    data object Back : PrivacyPolicyNavEvent
    data object Accepted : PrivacyPolicyNavEvent
}

@HiltViewModel
class PrivacyPolicyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val isFromProfile: Boolean =
        savedStateHandle.get<Boolean>("isFromProfile") ?: true

    private val _state = MutableStateFlow(
        PrivacyPolicyState(
            isFromProfile = isFromProfile,
            acceptButtonVisible = !isFromProfile,
        )
    )
    val state: StateFlow<PrivacyPolicyState> = _state.asStateFlow()

    private val _navEvents = Channel<PrivacyPolicyNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<PrivacyPolicyNavEvent> = _navEvents.receiveAsFlow()

    fun onEvent(event: PrivacyPolicyEvent) {
        when (event) {
            PrivacyPolicyEvent.Back -> _navEvents.trySend(PrivacyPolicyNavEvent.Back)
            PrivacyPolicyEvent.Accept -> _navEvents.trySend(PrivacyPolicyNavEvent.Accepted)
        }
    }
}
