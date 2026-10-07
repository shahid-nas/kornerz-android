package com.kornerz.core.storage.session

import com.kornerz.core.model.User

/**
 * Loading / error wrapper for session reads (architecture §2.2).
 *
 * The splash screen and the root NavHost both consume [data] from
 * [SessionRepository]. The very first emission is [Loading]; after
 * DataStore resolves it switches to either [Anonymous] or
 * [Authenticated].
 */
sealed interface SessionState {

    data object Loading : SessionState

    data object Anonymous : SessionState

    data class Authenticated(val user: User) : SessionState
}