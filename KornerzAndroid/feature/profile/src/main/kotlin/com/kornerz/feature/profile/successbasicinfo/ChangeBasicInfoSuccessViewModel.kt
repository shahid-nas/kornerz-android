package com.kornerz.feature.profile.successbasicinfo

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

data class ChangeBasicInfoSuccessState(
    val message: String = "",
)

sealed interface ChangeBasicInfoSuccessEvent {
    data object Close : ChangeBasicInfoSuccessEvent
}

@HiltViewModel
class ChangeBasicInfoSuccessViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ChangeBasicInfoSuccessState(message = savedStateHandle.get<String>("message").orEmpty())
    )
    val state: StateFlow<ChangeBasicInfoSuccessState> = _state.asStateFlow()

    private val _navEvents = Channel<Unit>(Channel.BUFFERED)
    val navEvents: Flow<Unit> = _navEvents.receiveAsFlow()

    fun onEvent(@Suppress("UNUSED_PARAMETER") event: ChangeBasicInfoSuccessEvent) {
        _navEvents.trySend(Unit)
    }
}
