package com.kornerz.core.network

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.network.auth.AuthApi
import com.kornerz.core.network.auth.AuthError
import com.kornerz.core.network.auth.AuthInterceptor
import com.kornerz.core.network.auth.AuthResult
import com.kornerz.core.network.auth.dto.ChangePasswordRequest
import com.kornerz.core.network.auth.dto.ConfirmPasswordResetCodeRequest
import com.kornerz.core.network.auth.dto.LoginRequest
import com.kornerz.core.network.auth.dto.RequestPasswordResetRequest
import com.kornerz.core.network.auth.dto.SignupRequest
import com.kornerz.core.network.fcm.DeviceIdProvider
import com.kornerz.core.network.fcm.DeviceTokenStore
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates [AuthApi] (network) and [SessionRepository] (persistence)
 * to expose a domain-shaped surface to the auth ViewModels. Every method
 * returns either a value or an [AuthError] — no Firebase specifics leak
 * past this layer.
 *
 * @Singleton scope (architecture §2.6); the whole app talks to session
 * state through this single instance.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val sessionRepository: SessionRepository,
    private val interceptor: AuthInterceptor,
    private val deviceTokenStore: DeviceTokenStore,
    private val deviceIdProvider: DeviceIdProvider,
) {

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    suspend fun login(email: String, password: String): AsyncValue<Unit> {
        return when (val result = authApi.login(LoginRequest(email = email, password = password))) {
            is AuthResult.Success -> {
                val user = authApi.fetchUser(result.data.userId).asAsyncValue()
                if (user is AsyncValue.Success) {
                    sessionRepository.setSession(
                        token = result.data.token,
                        expiresAt = Instant.parse(result.data.expiresAt),
                        user = user.data,
                    )
                }
                user.map { }
            }
            is AuthResult.Failure -> AsyncValue.Error(result.error.toThrowable())
        }
    }

    suspend fun signup(request: SignupRequest): AsyncValue<Unit> {
        return when (val result = authApi.signup(request)) {
            is AuthResult.Success -> {
                val user = authApi.fetchUser(result.data.userId).asAsyncValue()
                if (user is AsyncValue.Success) {
                    sessionRepository.setSession(
                        token = result.data.token,
                        expiresAt = Instant.parse(result.data.expiresAt),
                        user = user.data,
                    )
                }
                user.map { }
            }
            is AuthResult.Failure -> AsyncValue.Error(result.error.toThrowable())
        }
    }

    /**
     * Returns `AsyncValue.Success(true)` when a user with the supplied
     * [nickname] already exists, `AsyncValue.Success(false)` when the
     * nickname is free, or `AsyncValue.Error` on a network failure.
     * Powers the "nickname already taken" check on the signup wizard
     * (iOS parity — `UserRepository.checkIfNickNameExist(_:)`).
     */
    suspend fun checkIfNickNameExists(nickname: String): AsyncValue<Boolean> =
        when (val result = authApi.checkIfNickNameExists(nickname)) {
            is AuthResult.Success -> AsyncValue.Success(result.data)
            is AuthResult.Failure -> AsyncValue.Error(result.error.toThrowable())
        }

    /**
     * Returns `AsyncValue.Success(true)` when a user with the supplied
     * [email] already exists, `AsyncValue.Success(false)` when the email
     * is free, or `AsyncValue.Error` on a network failure. Powers the
     * "email already registered" check on the signup wizard
     * (iOS parity — `FireStoreServiceManager.checkIfEmailExist`).
     */
    suspend fun checkIfEmailExists(email: String): AsyncValue<Boolean> =
        when (val result = authApi.checkIfEmailExists(email)) {
            is AuthResult.Success -> AsyncValue.Success(result.data)
            is AuthResult.Failure -> AsyncValue.Error(result.error.toThrowable())
        }

    suspend fun requestPasswordReset(email: String): AsyncValue<Unit> {
        return when (
            val result = authApi.requestPasswordReset(
                RequestPasswordResetRequest(email = email),
            )
        ) {
            is AuthResult.Success -> AsyncValue.Success(Unit)
            is AuthResult.Failure -> AsyncValue.Error(result.error.toThrowable())
        }
    }

    suspend fun confirmPasswordResetCode(email: String, code: String): AsyncValue<String> {
        return when (
            val result = authApi.confirmPasswordResetCode(
                ConfirmPasswordResetCodeRequest(email = email, code = code),
            )
        ) {
            is AuthResult.Success -> AsyncValue.Success(result.data.resetToken)
            is AuthResult.Failure -> AsyncValue.Error(result.error.toThrowable())
        }
    }

    suspend fun changePassword(
        email: String,
        resetToken: String,
        newPassword: String,
    ): AsyncValue<Unit> {
        return when (
            val result = authApi.changePassword(
                ChangePasswordRequest(
                    email = email,
                    resetToken = resetToken,
                    newPassword = newPassword,
                ),
            )
        ) {
            is AuthResult.Success -> AsyncValue.Success(Unit)
            is AuthResult.Failure -> AsyncValue.Error(result.error.toThrowable())
        }
    }

    /**
     * Uploads the device's FCM token.
     *
     * The write goes through [DeviceTokenStore] — the same place the
     * FCM rotation handler writes — so the token always lands on
     * `Users/{email}.tokenPush`, the field iOS writes and the backend
     * reads. The earlier `AuthApi.updateFcmToken` path wrote
     * `users/{firebaseAuthUid}` and required a Firebase Auth user that
     * the port no longer creates, so it could never succeed.
     */
    suspend fun updateFcmToken(token: String): AsyncValue<Unit> {
        val user = (sessionRepository.data.first() as? SessionState.Authenticated)?.user
            ?: return AsyncValue.Error(
                IllegalStateException("Cannot upload an FCM token without an authenticated user"),
            )
        val deviceId = deviceIdProvider.deviceId()
        return deviceTokenStore.upsert(email = user.email, deviceId = deviceId, token = token)
            .fold(
                onSuccess = { AsyncValue.Success(Unit) },
                onFailure = { error ->
                    // FCM token rotation is a background task. A failure here must
                    // not wipe the persisted custom-auth session — that would force
                    // the user to log in again on every cold start.
                    AsyncValue.Error(error)
                },
            )
    }

    /**
     * Ends the session: clears the persisted [SessionRecord] so the
     * cold-start gate reads [SessionState.Anonymous] and opens the auth
     * graph instead of silently restoring the previous user.
     *
     * Mirrors iOS `UserProfileVC` line 230-231 (`setUserAsLoggedIn(false)`
     * + `deleteUserInfo()`), which tears the session down before the root
     * view is reset to the login screen.
     *
     * The remote FCM-token cleanup is **not** done here — it needs the
     * still-authenticated user, so the caller must run it *before*
     * calling this method (see `ShellViewModel.onLogout`).
     */
    suspend fun logout() {
        sessionRepository.clearSession()
    }

    /**
     * Removes the device's FCM token.
     *
     * The session is cleared either way: the user asked to sign out, so
     * the local session must not survive a failed remote cleanup.
     */
    suspend fun clearFcmToken(token: String): AsyncValue<Unit> {
        val user = (sessionRepository.data.first() as? SessionState.Authenticated)?.user
        if (user != null) {
            val deviceId = deviceIdProvider.deviceId()
            deviceTokenStore.clear(email = user.email, deviceId = deviceId, token = token)
        }
        sessionRepository.clearSession()
        return AsyncValue.Success(Unit)
    }

    private fun <T> AsyncValue<T>.map(transform: (T) -> Unit): AsyncValue<Unit> = when (this) {
        is AsyncValue.Success -> {
            transform(data)
            AsyncValue.Success(Unit)
        }
        is AsyncValue.Error -> this
        AsyncValue.Loading -> AsyncValue.Loading
    }
}

private fun <T> AuthResult<T>.asAsyncValue(): AsyncValue<T> = when (this) {
    is AuthResult.Success -> AsyncValue.Success(data)
    is AuthResult.Failure -> AsyncValue.Error(error.toThrowable())
}

private fun AuthError.toThrowable(): Throwable = when (this) {
    AuthError.InvalidCredentials -> AuthException(this, "Email or password is incorrect")
    AuthError.EmailAlreadyInUse -> AuthException(this, "This email is already registered")
    AuthError.NicknameAlreadyInUse -> AuthException(this, "This nickname is already taken")
    AuthError.InvalidCode -> AuthException(this, "Invalid or expired code")
    AuthError.NetworkError -> AuthException(this, "Connection error. Please try again.")
    AuthError.SessionExpired -> AuthException(this, "Your session has expired. Please log in again.")
    is AuthError.Unknown -> AuthException(this, message)
}

/**
 * Domain exception that wraps an [AuthError] so [AsyncValue.Error] can
 * stay Throwable-shaped (architecture §2.2) while the caller can still
 * pattern-match on [errorKind] to render a localised message.
 */
class AuthException(
    val errorKind: AuthError,
    message: String,
) : RuntimeException(message)