package com.kornerz.feature.profile.changebasicinfo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.EditableField
import com.kornerz.core.model.User
import com.kornerz.feature.profile.data.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChangeBasicInfoViewModel @Inject constructor(
    private val userRepository: UserProfileRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val field: EditableField = runCatching {
        EditableField.valueOf(savedStateHandle.get<String>(ARG_FIELD).orEmpty().uppercase())
    }.getOrDefault(EditableField.NAME)

    private val _state = MutableStateFlow(ChangeBasicInfoState(field = field))
    val state: StateFlow<ChangeBasicInfoState> = _state.asStateFlow()

    private val _navEvents = Channel<ChangeBasicInfoNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<ChangeBasicInfoNavEvent> = _navEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            val user = userRepository.observeSessionUser().first { it != null && it.id.isNotEmpty() }!!
            val original = originalValue(user)
            _state.update { it.copy(originalValue = original, draftValue = original) }
        }
    }

    private fun originalValue(user: User): String = when (field) {
        EditableField.NAME -> user.displayName
        EditableField.NICKNAME -> user.nickname
        EditableField.EMAIL -> user.email
        EditableField.PHONE -> user.phone.orEmpty()
        EditableField.PASSWORD -> ""
    }

    fun onEvent(event: ChangeBasicInfoEvent) {
        when (event) {
            is ChangeBasicInfoEvent.DraftValueChanged ->
                _state.update { it.copy(draftValue = event.value, error = null) }
            ChangeBasicInfoEvent.Save -> save()
            ChangeBasicInfoEvent.DismissError ->
                _state.update { it.copy(error = null) }
            ChangeBasicInfoEvent.ResetSuccess ->
                _state.update { it.copy(successMessage = null) }
        }
    }

    private fun save() {
        viewModelScope.launch {
            val current = _state.value
            if (!current.canSave) return@launch
            _state.update { it.copy(isSaving = true) }
            val user = userRepository.observeSessionUser()
                .first { it != null && it.id.isNotEmpty() }!!
            val result = when (field) {
                EditableField.NAME ->
                    userRepository.updateName(user.id, current.draftValue)
                EditableField.NICKNAME -> {
                    val exists = userRepository.checkIfNickNameExists(current.draftValue)
                    if (exists.isFailure) {
                        _state.update { it.copy(isSaving = false) }
                        _navEvents.trySend(
                            ChangeBasicInfoNavEvent.ShowError("Please try after sometime")
                        )
                        return@launch
                    }
                    if (exists.getOrThrow()) {
                        _state.update { it.copy(isSaving = false) }
                        _navEvents.trySend(
                            ChangeBasicInfoNavEvent.ShowError("Nickname not available")
                        )
                        return@launch
                    }
                    userRepository.updateNickName(user.id, current.draftValue)
                }
                else -> Result.success(user)
            }
            result.fold(
                onSuccess = {
                    val message = when (field) {
                        EditableField.NAME -> "name_changed_message"
                        EditableField.NICKNAME -> "nickname_changed_message"
                        else -> "saved"
                    }
                    _state.update { it.copy(isSaving = false) }
                    _navEvents.trySend(ChangeBasicInfoNavEvent.UpdatedSuccessfully(message))
                },
                onFailure = { t ->
                    _state.update { it.copy(isSaving = false) }
                    _navEvents.trySend(ChangeBasicInfoNavEvent.ShowError(t.message ?: "Error"))
                },
            )
        }
    }

    companion object {
        const val ARG_FIELD = "field"
    }
}
