package com.kornerz.feature.splash

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.network.forceupdate.ForceUpdateState

data class SplashState(
    val route: AsyncValue<SplashRoute> = AsyncValue.Loading,
    val forceUpdate: ForceUpdateState = ForceUpdateState.Idle,
)

sealed interface SplashRoute {
    data object AuthGraph : SplashRoute
    data object ShellGraph : SplashRoute
}

sealed interface SplashEvent {
    data class Resolved(val route: SplashRoute) : SplashEvent
}

sealed interface SplashNavEvent {
    data object NavigateToAuthGraph : SplashNavEvent
    data object NavigateToShellGraph : SplashNavEvent
}