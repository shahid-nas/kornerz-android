package com.kornerz.core.network.forceupdate

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.kornerz.core.model.AsyncValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Drives the non-dismissible force-update overlay from a Firebase Remote
 * Config parameter (`android_min_supported_version`). The splash
 * Composable collects [state] and overlays [com.kornerz.core.ui.components.ForceUpdateSheet]
 * whenever [ForceUpdateState.Required] is emitted.
 *
 * `@Singleton` scope (architecture §2.6).
 */
class ForceUpdateRepository(
    private val remoteConfig: FirebaseRemoteConfig,
    private val currentVersionCode: Int,
) {

    val state: Flow<ForceUpdateState> = flow {
        emit(ForceUpdateState.Idle)
        runCatching {
            remoteConfig.fetchAndActivate()
        }.onFailure {
            emit(ForceUpdateState.Failed)
            return@flow
        }
        val minVersion = remoteConfig.getLong(KEY_MIN_SUPPORTED_VERSION).toInt()
        emit(
            if (minVersion > currentVersionCode) {
                ForceUpdateState.Required(currentVersion = currentVersionCode, minVersion = minVersion)
            } else {
                ForceUpdateState.Idle
            },
        )
    }

    private companion object {
        const val KEY_MIN_SUPPORTED_VERSION = "android_min_supported_version"
    }
}

sealed interface ForceUpdateState {

    data object Idle : ForceUpdateState

    data class Required(val currentVersion: Int, val minVersion: Int) : ForceUpdateState

    data object Failed : ForceUpdateState
}

/** Convenience to read `state` as `AsyncValue<ForceUpdateState>`. */
fun ForceUpdateRepository.stateAsync(): Flow<AsyncValue<ForceUpdateState>> = flow {
    state.collect { value ->
        when (value) {
            ForceUpdateState.Idle -> emit(AsyncValue.Success(value))
            is ForceUpdateState.Required -> emit(AsyncValue.Success(value))
            ForceUpdateState.Failed -> emit(AsyncValue.Error(RuntimeException("Remote Config fetch failed")))
        }
    }
}