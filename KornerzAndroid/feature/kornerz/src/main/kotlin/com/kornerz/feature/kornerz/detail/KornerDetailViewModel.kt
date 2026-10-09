package com.kornerz.feature.kornerz.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.feature.kornerz.KornerzRepository
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

/**
 * Hilt-managed ViewModel for the Korner detail screen.
 *
 * Keyed by `kornerId` via Navigation-Compose's `SavedStateHandle` (per
 * design §"Decision: `KornerDetailViewModel` keyed by `kornerId` via
 * Hilt `SavedStateHandle`"). Initiates the read on `init` (per task
 * 7.6). Nook and member tile clicks forward to the navigation channel
 * — destinations resolve to "Coming in Phase N" snackbars until P4/P6
 * ship.
 */
@HiltViewModel
class KornerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: KornerzRepository,
) : ViewModel() {

    private val kornerId: String = savedStateHandle.get<String>(ARG_KORNER_ID).orEmpty()

    private val _state = MutableStateFlow(KornerDetailState())
    val state: StateFlow<KornerDetailState> = _state.asStateFlow()

    private val _navEvents = Channel<KornerDetailNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<KornerDetailNavEvent> = _navEvents.receiveAsFlow()

    init {
        if (kornerId.isNotBlank()) fetchData() else {
            _state.update { it.copy(detail = AsyncValue.Error(IllegalArgumentException("Missing kornerId"))) }
        }
    }

    fun onEvent(event: KornerDetailEvent) {
        when (event) {
            KornerDetailEvent.Retry -> fetchData()
            KornerDetailEvent.JoinCtaClicked -> { /* join flow deferred — snackbar fired at shell */ }
            is KornerDetailEvent.NookTileClicked -> emit(KornerDetailNavEvent.NavigateToNookDetail(event.nookId))
            is KornerDetailEvent.MemberTileClicked -> emit(KornerDetailNavEvent.NavigateToMemberProfile(event.userId))
        }
    }

    private fun fetchData() {
        viewModelScope.launch {
            _state.update { it.copy(detail = AsyncValue.Loading) }
            repository.fetchKornerDetail(kornerId).collect { result ->
                val next = result.fold(
                    onSuccess = { AsyncValue.Success(it) },
                    onFailure = { AsyncValue.Error(it) },
                )
                _state.update { it.copy(detail = next) }
            }
        }
    }

    private fun emit(event: KornerDetailNavEvent) {
        viewModelScope.launch { _navEvents.send(event) }
    }

    companion object {
        const val ARG_KORNER_ID = "kornerId"
    }
}
