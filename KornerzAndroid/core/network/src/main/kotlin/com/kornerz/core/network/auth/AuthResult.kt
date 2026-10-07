package com.kornerz.core.network.auth

import com.kornerz.core.model.AsyncValue

/**
 * Wrapper for the result of any auth backend call. Used by
 * [com.kornerz.core.network.AuthRepository] to expose a uniform shape
 * to the auth ViewModels; consumers (Login / Signup / Password-Recovery)
 * pattern-match on `AsyncValue<T>` per architecture §2.2.
 */
sealed interface AuthResult<out T> {

    data class Success<T>(val data: T) : AuthResult<T>

    data class Failure(val error: AuthError) : AuthResult<Nothing>
}

/** Domain errors surfaced by [AuthApi] implementations. */
sealed interface AuthError {

    data object InvalidCredentials : AuthError

    data object EmailAlreadyInUse : AuthError

    data object InvalidCode : AuthError

    data object NetworkError : AuthError

    data object SessionExpired : AuthError

    data class Unknown(val message: String) : AuthError
}

/** Convenience adapter so `AsyncValue<T>` and `AuthResult<T>` interoperate. */
fun <T> AsyncValue<T>.toResultOrNull(): T? = when (this) {
    is AsyncValue.Success -> data
    else -> null
}