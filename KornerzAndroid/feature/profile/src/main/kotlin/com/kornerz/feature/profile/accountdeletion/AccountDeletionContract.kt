package com.kornerz.feature.profile.accountdeletion

import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.feature.profile.data.UserProfileRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared event bus for the `.userDeletedHisAccount` signal. Mirrors
 * iOS `NotificationCenter.default.post(name: .userDeletedHisAccount,
 * object: nil)` (P6, task 7.3).
 */
@Singleton
class UserDeletedHisAccountEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val events: SharedFlow<Unit> = _events.asSharedFlow()
    suspend fun emit() { _events.emit(Unit) }
}

data class AccountDeletionState(
    val showConfirmDialog: Boolean = false,
    val isDeleting: Boolean = false,
    val error: String? = null,
)

sealed interface AccountDeletionEvent {
    data object OpenConfirm : AccountDeletionEvent
    data object Cancel : AccountDeletionEvent
    data object Confirm : AccountDeletionEvent
}

sealed interface AccountDeletionNavEvent {
    data object NavigateToAuth : AccountDeletionNavEvent
    data class ShowError(val message: String) : AccountDeletionNavEvent
}
