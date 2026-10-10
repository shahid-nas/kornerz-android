package com.kornerz.feature.nooks.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.feature.nooks.NooksRoute
import com.kornerz.feature.nooks.data.NooksRepository
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
 * Hilt-managed ViewModel for the Nook detail screen. Keyed by
 * `nookId` via Navigation-Compose's `SavedStateHandle` (per design
 * §"Decision: `NookDetailViewModel` keyed by `nookId` via Hilt
 * `SavedStateHandle`"). Initiates the read on `init`.
 */
@HiltViewModel
class NookDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NooksRepository,
) : ViewModel() {

    private val nookId: String = savedStateHandle.get<String>(NooksRoute.Detail.ARG_NOOK_ID).orEmpty()

    private val _state = MutableStateFlow(NookDetailState())
    val state: StateFlow<NookDetailState> = _state.asStateFlow()

    private val _navEvents = Channel<NookDetailNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<NookDetailNavEvent> = _navEvents.receiveAsFlow()

    init {
        if (nookId.isNotBlank()) fetchData() else {
            _state.update { it.copy(detail = AsyncValue.Error(IllegalArgumentException("Missing nookId"))) }
        }
    }

    fun onEvent(event: NookDetailEvent) {
        when (event) {
            NookDetailEvent.Retry -> fetchData()
            NookDetailEvent.BackToKornerClicked -> {
                val kornerId = (_state.value.detail as? AsyncValue.Success)?.data?.korner?.id
                if (kornerId != null) {
                    emit(NookDetailNavEvent.NavigateToKornerDetail(kornerId))
                }
            }
        }
    }

    fun retry() = fetchData()

    private fun fetchData() {
        viewModelScope.launch {
            _state.update { it.copy(detail = AsyncValue.Loading) }
            repository.fetchNookDetail(nookId).collect { result ->
                val next = result.fold(
                    onSuccess = { AsyncValue.Success(it) },
                    onFailure = { AsyncValue.Error(it) },
                )
                _state.update { it.copy(detail = next) }
            }
        }
    }

    private fun emit(event: NookDetailNavEvent) {
        viewModelScope.launch { _navEvents.send(event) }
    }

    companion object {
        const val ARG_NOOK_ID: String = NooksRoute.Detail.ARG_NOOK_ID
    }
}
