package com.kornerz.core.storage.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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