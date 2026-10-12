package com.kornerz.feature.kornerz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.ui.components.header.TabHeaderViewModel
import com.kornerz.feature.kornerz.sections.NewThisWeekKornerzSectionViewModel
import com.kornerz.feature.kornerz.sections.RecommendedKornerzSectionViewModel
import com.kornerz.feature.kornerz.sections.YourKornerzSectionViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hilt-managed ViewModel for the Kornerz tab. Composes the three
 * section sub-VMs (Your / Recommended / New this week) into a single
 * `KornerzState`, mirroring the iOS `KornerzTabViewModel` which owns
 * three `KornerzCarouselViewModel` instances.
 *
 * Also composes the shared `TabHeaderViewModel` so the avatar +
 * points row sits above the carousels — matches iOS, where every tab
 * embeds `HomeHeaderView` at the top of its scroll content.
 *
 * Architecture §2.1 / §2.4 / §2.6: emits navigation through a buffered
 * [Channel]; composes screen state from independent sub-VMs.
 */
@HiltViewModel
class KornerzViewModel @Inject constructor(
    private val headerVm: TabHeaderViewModel,
    private val yourKornerzVm: YourKornerzSectionViewModel,
    private val recommendedVm: RecommendedKornerzSectionViewModel,
    private val newThisWeekVm: NewThisWeekKornerzSectionViewModel,
) : ViewModel() {

    val state: StateFlow<KornerzState> = combine(
        headerVm.state,
        yourKornerzVm.state,
        combine(recommendedVm.state, newThisWeekVm.state) { r, n -> r to n },
    ) { header, your, recommendedAndNew ->
        KornerzState(
            header = header,
            yourKornerz = your,
            recommended = recommendedAndNew.first,
            newThisWeek = recommendedAndNew.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = KornerzState(),
    )

    private val _navEvents = Channel<KornerzNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<KornerzNavEvent> = _navEvents.receiveAsFlow()

    fun onEvent(event: KornerzEvent) {
        when (event) {
            KornerzEvent.Refresh -> refresh()
            KornerzEvent.Retry -> refresh()
            KornerzEvent.AvatarTapped -> emit(KornerzNavEvent.NavigateToProfile)
            is KornerzEvent.RowClicked -> emit(KornerzNavEvent.NavigateToKornerDetail(event.kornerId))
        }
    }

    init {
        refresh()
    }

    fun refresh() {
        headerVm.fetchData()
        yourKornerzVm.fetchData()
        recommendedVm.fetchData()
        newThisWeekVm.fetchData()
    }

    private fun emit(event: KornerzNavEvent) {
        viewModelScope.launch { _navEvents.send(event) }
    }
}