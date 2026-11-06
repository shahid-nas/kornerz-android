package com.kornerz.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.ui.components.header.TabHeaderViewModel
import com.kornerz.feature.home.loop.LoopOfDayViewModel
import com.kornerz.feature.home.recent_loops.RecentLoopsViewModel
import com.kornerz.feature.home.upcoming_nooks.UpcomingNooksViewModel
import com.kornerz.feature.home.your_kornerz.YourKornerzViewModel
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

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val headerVm: TabHeaderViewModel,
    private val loopOfDayVm: LoopOfDayViewModel,
    private val upcomingNooksVm: UpcomingNooksViewModel,
    private val yourKornerzVm: YourKornerzViewModel,
    private val recentLoopsVm: RecentLoopsViewModel,
) : ViewModel() {

    val state: StateFlow<HomeState> = combine(
        headerVm.state,
        loopOfDayVm.state,
        combine(
            upcomingNooksVm.state,
            yourKornerzVm.state,
            recentLoopsVm.state,
        ) { nooks, kornerz, loops ->
            Triple(nooks, kornerz, loops)
        },
    ) { header, loop, triple ->
        HomeState(
            header = header,
            loopOfDay = loop,
            upcomingNooks = triple.first,
            yourKornerz = triple.second,
            recentLoops = triple.third,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeState(),
    )

    private val _navEvents = Channel<HomeNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<HomeNavEvent> = _navEvents.receiveAsFlow()

    init {
        refresh()
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.Refresh -> refresh()
            HomeEvent.LoopOfDayRetry -> loopOfDayVm.fetchData()
            HomeEvent.UpcomingNooksRetry -> upcomingNooksVm.fetchData()
            HomeEvent.YourKornerzRetry -> yourKornerzVm.fetchData()
            HomeEvent.RecentLoopsRetry -> recentLoopsVm.fetchData()
            HomeEvent.AvatarTapped -> emit(HomeNavEvent.NavigateToProfile)
            is HomeEvent.LoopOfDayClicked ->
                emit(
                    HomeNavEvent.NavigateToLoopDetail(
                        loopId = event.loopId,
                        accentColorHex = event.accentColorHex,
                    ),
                )
            is HomeEvent.NookClicked -> emit(HomeNavEvent.NavigateToEventDetail(event.nookId))
            is HomeEvent.KornerClicked -> emit(
                HomeNavEvent.NavigateToKornerDetail(
                    kornerId = event.kornerId,
                    themeColorHex = event.themeColorHex,
                ),
            )
        }
    }

    private fun refresh() {
        headerVm.fetchData()
        loopOfDayVm.fetchData()
        upcomingNooksVm.fetchData()
        yourKornerzVm.fetchData()
        recentLoopsVm.fetchData()
    }

    private fun emit(event: HomeNavEvent) {
        viewModelScope.launch { _navEvents.send(event) }
    }
}