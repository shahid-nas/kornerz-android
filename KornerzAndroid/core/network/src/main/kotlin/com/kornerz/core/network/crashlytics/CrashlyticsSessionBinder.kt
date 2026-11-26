package com.kornerz.core.network.crashlytics

import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps Crashlytics' `userId` in step with the auth session.
 *
 * Crashes that happen before login carry no user id; once the session
 * becomes [SessionState.Authenticated] every subsequent report is
 * attributed to that user; logout clears the id so the next account's
 * crashes are not mixed in.
 *
 * Started once from [KornerzApplication.onCreate] on the application
 * scope — the collector lives for the process lifetime.
 */
@Singleton
class CrashlyticsSessionBinder @Inject constructor(
    private val crashReporter: CrashReporter,
    private val sessionRepository: SessionRepository,
) {

    /**
     * Starts observing [SessionRepository.data]. Safe to call more
     * than once only if each call uses a distinct scope that is never
     * cancelled; the application calls it exactly once.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            sessionRepository.data.collectLatest { state ->
                when (state) {
                    is SessionState.Authenticated -> {
                        val id = state.user.userCode?.takeIf { it.isNotBlank() }
                            ?: state.user.id.takeIf { it.isNotBlank() }
                        crashReporter.setUserId(id)
                        crashReporter.log("session: authenticated as ${state.user.email}")
                    }
                    SessionState.Anonymous -> {
                        crashReporter.setUserId(null)
                        crashReporter.log("session: anonymous")
                    }
                    SessionState.Loading -> Unit
                }
            }
        }
    }
}
