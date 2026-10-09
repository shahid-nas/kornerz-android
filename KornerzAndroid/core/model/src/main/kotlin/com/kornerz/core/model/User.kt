package com.kornerz.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The canonical Kornerz user record. Mirrors the iOS `User` struct
 * (`kornerz/Kornerz/Models/User.swift`); Android-specific JSON names are
 * chosen to match the iOS `CodingKeys` so the same Firestore document
 * shape works on both platforms.
 *
 * Subscription fields (`subscriptionExpiryDate`, `subscriptionType`,
 * `subscriptionPlatform`) are nullable because the majority of users in
 * P1 are free tier; they are populated by the billing flow introduced in
 * P8.
 */
@Serializable
data class User(
    val id: String,
    val email: String,
    @SerialName("name") val displayName: String,
    @SerialName("nickname") val nickname: String,
    @SerialName("codeUser") val userCode: String? = null,
    @SerialName("number") val phone: String? = null,
    @SerialName("countryCode") val countryCode: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("photoUrl") val photoUrl: String? = null,
    @SerialName("tokenPush") val fcmToken: String? = null,
    @SerialName("isUserPremium") val isPremium: Boolean = false,
    val subscriptionExpiryDate: String? = null,
    val subscriptionType: String? = null,
    val subscriptionPlatform: String? = null,
    @SerialName("userDateRegister") val createdAt: String? = null,
)