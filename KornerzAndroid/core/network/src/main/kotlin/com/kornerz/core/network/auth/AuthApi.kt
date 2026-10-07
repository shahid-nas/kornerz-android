package com.kornerz.core.network.auth

import com.kornerz.core.model.User
import com.kornerz.core.network.auth.dto.ChangePasswordRequest
import com.kornerz.core.network.auth.dto.ChangePasswordResponse
import com.kornerz.core.network.auth.dto.ClearFcmTokenRequest
import com.kornerz.core.network.auth.dto.ClearFcmTokenResponse
import com.kornerz.core.network.auth.dto.ConfirmPasswordResetCodeRequest
import com.kornerz.core.network.auth.dto.ConfirmPasswordResetCodeResponse
import com.kornerz.core.network.auth.dto.LoginRequest
import com.kornerz.core.network.auth.dto.LoginResponse
import com.kornerz.core.network.auth.dto.RequestPasswordResetRequest
import com.kornerz.core.network.auth.dto.RequestPasswordResetResponse
import com.kornerz.core.network.auth.dto.SignupRequest
import com.kornerz.core.network.auth.dto.SignupResponse
import com.kornerz.core.network.auth.dto.UpdateFcmTokenRequest
import com.kornerz.core.network.auth.dto.UpdateFcmTokenResponse
import kotlinx.datetime.Instant

/**
 * One suspend method per custom-auth Cloud Function. The contract mirrors
 * the iOS app's `UserRepository` + `ResetPasswordRepository`; see
 * `kornerz/Kornerz/Backend/Repositories/UserRepository.swift` and
 * `kornerz/Kornerz/Backend/Repositories/ForgotPasswordRepository.swift`
 * for the upstream definitions.
 *
 * All methods return an [AuthResult] so the caller (`AuthRepository`) can
 * translate backend errors into the [AuthError] taxonomy without leaking
 * Cloud-Function-specific exception shapes to the UI layer.
 */
interface AuthApi {

    suspend fun login(request: LoginRequest): AuthResult<LoginResponse>

    suspend fun signup(request: SignupRequest): AuthResult<SignupResponse>

    suspend fun requestPasswordReset(
        request: RequestPasswordResetRequest,
    ): AuthResult<RequestPasswordResetResponse>

    suspend fun confirmPasswordResetCode(
        request: ConfirmPasswordResetCodeRequest,
    ): AuthResult<ConfirmPasswordResetCodeResponse>

    suspend fun changePassword(
        request: ChangePasswordRequest,
    ): AuthResult<ChangePasswordResponse>

    suspend fun updateFcmToken(
        request: UpdateFcmTokenRequest,
    ): AuthResult<UpdateFcmTokenResponse>

    suspend fun clearFcmToken(
        request: ClearFcmTokenRequest,
    ): AuthResult<ClearFcmTokenResponse>

    /**
     * Fetches the canonical [User] document after a sign-up or login. The
     * auth Cloud Functions only return a `userId` + token; this second hop
     * loads the full profile from Firestore so the [SessionRepository]
     * can persist a fully-populated `User`.
     */
    suspend fun fetchUser(userId: String): AuthResult<User>

    /** Clock used to materialise the token `expiresAt` in tests. */
    fun now(): Instant
}