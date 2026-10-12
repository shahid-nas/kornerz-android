package com.kornerz.feature.profile.accountdeletion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
class AccountDeletionViewModel @Inject constructor(
    private val userRepository: UserProfileRepository,
    private val eventBus: UserDeletedHisAccountEventBus,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountDeletionState())
    val state: StateFlow<AccountDeletionState> = _state.asStateFlow()

    private val _navEvents = Channel<AccountDeletionNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<AccountDeletionNavEvent> = _navEvents.receiveAsFlow()

    fun onEvent(event: AccountDeletionEvent) {
        when (event) {
            AccountDeletionEvent.OpenConfirm ->
                _state.update { it.copy(showConfirmDialog = true) }
            AccountDeletionEvent.Cancel ->
                _state.update { it.copy(showConfirmDialog = false) }
            AccountDeletionEvent.Confirm -> confirm()
        }
    }

    private fun confirm() {
        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true) }
            val user = userRepository.observeSessionUser().first { it != null && it.id.isNotEmpty() }
            val userCode = user?.id
            val result = userRepository.deleteAccount()
            if (result.isFailure) {
                _state.update { it.copy(isDeleting = false, showConfirmDialog = false) }
                _navEvents.trySend(AccountDeletionNavEvent.ShowError(result.exceptionOrNull()?.message ?: "Error"))
                return@launch
            }
            if (userCode != null) {
                userRepository.removeFcmToken(userCode)
            }
            userRepository.clearLocalSession()
            eventBus.emit()
            _state.update { it.copy(isDeleting = false, showConfirmDialog = false) }
            _navEvents.trySend(AccountDeletionNavEvent.NavigateToAuth)
        }
    }
}
