package com.kornerz.feature.kornerz.detail

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared event bus for the "refresh the Korner detail" signal. Mirrors
 * iOS `NotificationCenter.default.post(name: .refreshKornerDetail,
 * object: nil)` (`kornerz/Kornerz/DeepLinks/DeeplinkManager.swift:181-183`).
 *
 * iOS posts this notification when a **video-call** push arrives and the
 * Korner the call belongs to is *already on screen*: instead of pushing
 * a second copy of the Korner detail, the open one re-reads its data, so
 * the user sees the live session appear in the screen they are already
 * looking at. The check that decides between the two paths is
 * `checkIfKornerDetailAlreadyOpened` (`DeeplinkManager.swift:168-179`).
 *
 * Android needs the same signal because the Korner detail is a
 * destination **inside a nested `NavHost`**: the shell cannot reach the
 * screen's ViewModel to call a refresh method on it directly, so the
 * request travels through this bus instead — the same shape as iOS's
 * `NotificationCenter`.
 *
 * The bus carries the **korner id** the refresh is for, so the shell
 * can decide whether the open screen is the one being asked about, and
 * so a stale signal cannot refresh a screen the user has since left.
 */
@Singleton
class KornerDetailRefreshBus @Inject constructor() {

    /**
     * The refresh requests, with the **most recent one replayed**.
     *
     * `replay = 1` matters because the requester and the screen live on
     * different coroutines: the shell emits the request, and the Korner
     * detail's view model collects it. Without the replay, a request
     * emitted in the window between the screen being torn down and its
     * collector being re-attached would be dropped, and the refresh
     * would silently not happen.
     *
     * With the replay, a subscriber that attaches later still sees the
     * last request — and the view model filters it by korner id, so a
     * replayed request for a different Korner is ignored.
     */
    private val _requests = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 4)

    /** The korner ids a refresh has been requested for. */
    val requests: SharedFlow<String> = _requests.asSharedFlow()

    /**
     * Requests a refresh of the Korner detail for [kornerId].
     *
     * `tryEmit` rather than `emit`, so the caller — the shell's
     * coroutine — is never suspended by a slow subscriber. A refresh
     * request that cannot be delivered immediately is dropped by the
     * flow's own overflow policy rather than blocking the navigation.
     */
    fun requestRefresh(kornerId: String) {
        _requests.tryEmit(kornerId)
    }
}
