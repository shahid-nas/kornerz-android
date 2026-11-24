package com.kornerz.core.network.user

import android.util.Log
import com.kornerz.core.model.SubscriptionDiagnostics
import com.kornerz.core.network.auth.AuthApi
import com.kornerz.core.network.auth.AuthError
import com.kornerz.core.network.auth.AuthResult
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-reads the signed-in user's document from Firestore and writes the
 * authoritative copy back into the local session store.
 *
 * **Why this exists.** The session store (`SessionRepository`) caches the
 * whole `User` at login and nothing else ever refreshes it, so the cached
 * copy goes stale for the whole life of the app. A user who updates their
 * name from the web, or subscribes from the iOS app, or whose
 * subscription lapses, keeps seeing the login-time snapshot on Android —
 * including a stale `subscriptionExpiryDate`, which is the input to
 * `User.isUserPremiumNow`, the single premium decision authority on both
 * platforms.
 *
 * **iOS parity.** This is the Android counterpart of iOS `UserSyncService`
 * (`kornerz/Kornerz/Backend/AutoStartServices/UserSyncService.swift:22-48`),
 * which iOS starts from `AppTabController.startServices()` on every cold
 * start: it calls `UserRepository.getUser(userId)` — a `source: .server`
 * read that bypasses the Firestore cache — and writes the result into
 * both `AppHelper.shared.user` and `UserDefaults.standard.saveUserInfo`.
 * iOS reads the user document by `userId`, which is the document id, and
 * the document id *is* the email on both platforms (`Users/{email}`) —
 * see `UserFirestoreApi`'s KDoc in `feature/profile`.
 *
 * **Where it runs.** The shell calls [refreshCurrentUser] once per cold
 * start, *before* the entitlement reconciliation and the paywall gate —
 * otherwise the gate would evaluate against the stale login-time expiry
 * that this refresh exists to replace.
 *
 * **Failure is non-destructive.** A network outage, an offline device, or
 * a deleted user document leaves the cached session exactly as it was. An
 * unreachable backend must never cost a paying subscriber their premium
 * state, and must never sign a user out.
 */
@Singleton
class UserProfileRefresher @Inject constructor(
    private val authApi: AuthApi,
    private val sessionRepository: SessionRepository,
) {

    /**
     * Refreshes the cached user for the current session.
     *
     * Returns `Result.success(Unit)` when the cache was updated, and
     * `Result.failure` when there was nothing to refresh (anonymous
     * session) or the read failed. The cached session is left untouched
     * in the failure case.
     */
    suspend fun refreshCurrentUser(): Result<Unit> {
        val user = (sessionRepository.data.first() as? SessionState.Authenticated)?.user
            ?: return Result.failure(RefreshFailure.AnonymousSession)

        val email = user.email.takeIf { it.isNotBlank() }
            ?: return Result.failure(RefreshFailure.MissingIdentifier)

        return when (val result = authApi.fetchUser(email)) {
            is AuthResult.Success -> {
                val authoritative = result.data
                // The expiry is logged in full — the raw string, the parsed
                // instant, and the premium verdict derived from them. This
                // is the single read the paywall gate depends on, so a
                // stale, missing, or unparseable expiry is visible here
                // rather than only as a paywall that never appears.
                SubscriptionDiagnostics.log(
                    "1-refresh",
                    "refreshed the cached user for $email — " +
                        "subscriptionExpiryDate=${authoritative.subscriptionExpiryDate} " +
                        "parsed=${authoritative.subscriptionExpiryInstant} " +
                        "isUserPremiumNow=${authoritative.isUserPremiumNow} " +
                        "subscriptionType=${authoritative.subscriptionType} " +
                        "subscriptionPlatform=${authoritative.subscriptionPlatform}",
                )
                sessionRepository.updateUser { authoritative }
                    .onFailure { error ->
                        Log.w(TAG, "Could not write the refreshed user to the session store", error)
                    }
                    .map { }
            }
            is AuthResult.Failure -> {
                SubscriptionDiagnostics.fail(
                    "1-refresh",
                    "could not read the user document for $email (${result.error}); " +
                        "the cached session is left as it was",
                )
                Result.failure(
                    RefreshFailure.FetchFailed(result.error),
                )
            }
        }
    }

    private companion object {
        const val TAG = "UserProfileRefresher"
    }
}

/**
 * Why a refresh did not happen. Distinct from a transport failure so the
 * caller can tell "there was no session to refresh" (not an error) from
 * "the read failed" (a transient condition worth retrying).
 *
 * A `Throwable` because it travels inside a `Result.failure`, which is
 * the shape every repository in this codebase uses for a failed
 * operation.
 */
sealed class RefreshFailure(message: String) : RuntimeException(message) {

    /** The session is anonymous — there is no user to refresh. */
    data object AnonymousSession : RefreshFailure("Anonymous session; nothing to refresh")

    /** The cached user carries no email, so no document can be addressed. */
    data object MissingIdentifier : RefreshFailure("Authenticated session with a blank email")

    /** The read failed. [error] carries the underlying backend error. */
    data class FetchFailed(val error: AuthError) : RefreshFailure(
        "Could not read the user document: $error",
    )
}
