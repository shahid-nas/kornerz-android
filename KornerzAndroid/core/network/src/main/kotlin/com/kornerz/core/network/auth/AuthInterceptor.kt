package com.kornerz.core.network.auth

import com.kornerz.core.storage.session.SessionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects "session expired" responses from the auth backend and triggers
 * a [SessionRepository.clearSession]. The interceptor is intentionally
 * minimal in P1 — there is no real HTTP client to wrap (calls go through
 * Firebase Functions / Firestore). It exists so that ViewModels can
 * observe an [AuthError.SessionExpired] result and route the user back to
 * Login without coupling to Firebase specifics.
 *
 * In P2 the implementation will be replaced with an OkHttp interceptor
 * that injects the `Authorization: Bearer <token>` header on every
 * outbound call and matches the Cloud Function's `unauthenticated`
 * response shape.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionRepository: SessionRepository,
) {

    suspend fun onSessionExpired() {
        sessionRepository.clearSession()
    }

    /** True if the error message indicates an expired / unauthenticated session. */
    fun isSessionExpired(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val normalized = message.lowercase()
        return normalized.contains("expired") ||
            normalized.contains("unauthenticated") ||
            normalized.contains("invalid token")
    }
}