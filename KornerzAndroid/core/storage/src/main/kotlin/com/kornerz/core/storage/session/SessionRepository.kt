package com.kornerz.core.storage.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.kornerz.core.model.SubscriptionDiagnostics
import com.kornerz.core.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the user's auth session (token + expiry +
 * cached [User]). Backed by a per-process DataStore file; reads return
 * a [Flow] of [SessionState] (architecture §2.3 — `Flow<T>` for streams).
 *
 * The instance is `@Singleton`-scoped (architecture §2.6) so the same
 * DataStore file is shared by every consumer — the splash Composable,
 * the root NavHost, and [com.kornerz.core.network.AuthRepository].
 */
@Singleton
class SessionRepository @Inject constructor(
    private val dataStore: DataStore<SessionRecord>,
) {

    /**
     * Stream of session state. Emits [SessionState.Loading] until the
     * first DataStore read completes, then [Anonymous] / [Authenticated]
     * for every subsequent change.
     */
    val data: Flow<SessionState> = dataStore.data
        .catch { emit(SessionRecord("", "", emptyUser())) }
        .map { record -> record.toState() }

    suspend fun setSession(token: String, expiresAt: Instant, user: User) {
        dataStore.updateData { current ->
            SessionRecord(
                token = token,
                expiresAt = expiresAt.toString(),
                user = user,
            )
        }
        // The session cache is what the paywall gate reads before the
        // cold-start refresh lands, so the entitlement it now holds is
        // logged — a login that captured a stale or empty expiry is
        // otherwise indistinguishable from a correct one.
        SubscriptionDiagnostics.log(
            "1-refresh",
            "wrote the session for ${user.email} — " +
                "subscriptionExpiryDate=${user.subscriptionExpiryDate} " +
                "parsed=${user.subscriptionExpiryInstant} " +
                "isUserPremiumNow=${user.isUserPremiumNow}",
        )
    }

    suspend fun clearSession() {
        dataStore.updateData { current ->
            // Re-write as a sentinel "anonymous" record so existing readers
            // get an Anonymous state immediately; corruption handler will
            // recover if the file goes missing.
            SessionRecord(
                token = "",
                expiresAt = "",
                user = emptyUser(),
            )
        }
    }

    /**
     * Patches the cached [User] without touching the auth credentials.
     *
     * Reads the current [SessionRecord], applies [transform] to its
     * `user` field, and writes the record back with `token` and
     * `expiresAt` preserved **verbatim** — all inside a single
     * `dataStore.updateData` block, so the write stays atomic and the
     * file can never be left holding an updated user beside a stale
     * token.
     *
     * Exists because [setSession] is the only other write path and it
     * demands a live auth token that the P8 billing flow does not have
     * and must not need. Called after every successful purchase, revoke,
     * and server reconciliation so the cached user's premium fields stay
     * in step with Firestore.
     *
     * The anonymous-detection invariants in [SessionRecord.toState] are
     * untouched: a patch over an anonymous record stays anonymous, and a
     * patch over a valid authenticated record never emits a transient
     * `SessionState.Anonymous`. Returns `Result.failure` on any read or
     * write error rather than throwing.
     */
    suspend fun updateUser(transform: (User) -> User): Result<Unit> = runCatching {
        dataStore.updateData { current ->
            val updated = transform(current.user)
            // The cached user is what the paywall gate reads, so the
            // entitlement fields it now holds are logged — a refresh that
            // silently wrote an empty expiry, or failed to write at all,
            // is otherwise invisible.
            SubscriptionDiagnostics.log(
                "1-refresh",
                "wrote the refreshed user to the session cache — " +
                    "subscriptionExpiryDate=${updated.subscriptionExpiryDate} " +
                    "parsed=${updated.subscriptionExpiryInstant} " +
                    "isUserPremiumNow=${updated.isUserPremiumNow}",
            )
            current.copy(user = updated)
        }
        Unit
    }

    /**
     * Synchronous last-known token access used by the FCM registrar on
     * `Application.onCreate` before any collector has subscribed. Returns
     * the empty string if no session has been loaded yet — callers must
     * treat that as anonymous.
     */
    fun currentTokenBlocking(): String = runBlocking {
        dataStore.data
            .map { record -> record.takeIf { it.token.isNotEmpty() }?.token.orEmpty() }
            .catch { emit("") }
            .first()
    }

    private fun emptyUser(): User = User(
        id = "",
        email = "",
        displayName = "",
        nickname = "",
    )

    companion object {
        const val FILE_NAME = "kornerz_session.json"

        fun create(context: Context): SessionRepository {
            val store = DataStoreFactory.create(
                serializer = SessionSerializer,
                produceFile = { context.filesDir.resolve("datastore/$FILE_NAME") },
                corruptionHandler = ReplaceFileCorruptionHandler { _ ->
                    SessionRecord(
                        token = "",
                        expiresAt = "",
                        user = User(id = "", email = "", displayName = "", nickname = ""),
                    )
                },
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            )
            return SessionRepository(store)
        }
    }
}

private fun SessionRecord.toState(): SessionState {
    if (token.isEmpty() || user.id.isEmpty()) return SessionState.Anonymous
    val expiry = runCatching { Instant.parse(expiresAt) }.getOrNull()
        ?: return SessionState.Anonymous
    return if (expiry < kotlinx.datetime.Clock.System.now()) {
        SessionState.Anonymous
    } else {
        SessionState.Authenticated(user)
    }
}