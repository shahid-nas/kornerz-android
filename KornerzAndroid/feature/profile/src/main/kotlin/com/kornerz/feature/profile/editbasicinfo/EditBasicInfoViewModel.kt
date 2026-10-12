package com.kornerz.feature.profile.editbasicinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.EditableField
import com.kornerz.feature.profile.data.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditBasicInfoViewModel @Inject constructor(
    private val userRepository: UserProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EditBasicInfoState())
    val state: StateFlow<EditBasicInfoState> = _state.asStateFlow()

    private val _navEvents = Channel<EditBasicInfoNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<EditBasicInfoNavEvent> = _navEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            userRepository.observeSessionUser().collect { user ->
                if (user != null) {
                    _state.update { it.copy(user = user) }
                }
            }
        }
    }

    fun onEvent(event: EditBasicInfoEvent) {
        when (event) {
            is EditBasicInfoEvent.UserChanged ->
                _state.update { it.copy(user = event.user) }
        }
    }

    fun onChangeField(field: EditableField) {
        _navEvents.trySend(EditBasicInfoNavEvent.OpenChangeScreen(field))
    }

    fun onBack() = _navEvents.trySend(EditBasicInfoNavEvent.Back)
}
