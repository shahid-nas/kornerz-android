package com.kornerz.core.network.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class LoginResponse(val token: String, val expiresAt: String, val userId: String)

@Serializable
data class SignupRequest(
    val displayName: String,
    val nickname: String,
    val email: String,
    val password: String,
    val phone: String? = null,
    val countryCode: String? = null,
    val language: String? = null,
    val interests: List<String> = emptyList(),
    val photoUrl: String? = null,
)

@Serializable
data class SignupResponse(val token: String, val expiresAt: String, val userId: String)

@Serializable
data class RequestPasswordResetRequest(val email: String)

@Serializable
data class RequestPasswordResetResponse(val sent: Boolean)

@Serializable
data class ConfirmPasswordResetCodeRequest(val email: String, val code: String)

@Serializable
data class ConfirmPasswordResetCodeResponse(val resetToken: String)

@Serializable
data class ChangePasswordRequest(
    val email: String,
    val resetToken: String,
    val newPassword: String,
)

@Serializable
data class ChangePasswordResponse(val updated: Boolean)

@Serializable
data class UpdateFcmTokenRequest(val token: String)

@Serializable
data class UpdateFcmTokenResponse(val updated: Boolean)

@Serializable
data class ClearFcmTokenRequest(val token: String)

@Serializable
data class ClearFcmTokenResponse(val updated: Boolean)

@Serializable
data class GetUserResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val nickname: String,
    val phone: String? = null,
    val countryCode: String? = null,
    val language: String? = null,
    val photoUrl: String? = null,
    val fcmToken: String? = null,
    val isPremium: Boolean = false,
    val subscriptionExpiryDate: String? = null,
    val subscriptionType: String? = null,
    val subscriptionPlatform: String? = null,
    val createdAt: String? = null,
)