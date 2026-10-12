package com.kornerz.feature.profile.view

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.User

/**
 * UI state for the bottom-nav Profile tab (P6, screen 7.1).
 */
data class ProfileState(
    val user: AsyncValue<User> = AsyncValue.Loading,
    val ownedCommunityCount: Int = 0,
    val isUploadingImage: Boolean = false,
)

sealed interface ProfileEvent {
    data class UserChanged(val user: User) : ProfileEvent
    data class CommunityCountChanged(val count: Int) : ProfileEvent
    data object ImageUploadStarted : ProfileEvent
    data class ImageUploadFinished(val success: Boolean) : ProfileEvent
    data class ErrorOccurred(val message: String) : ProfileEvent
}

sealed interface ProfileNavEvent {
    data object OpenNotifications : ProfileNavEvent
    data object OpenEditBasicInfo : ProfileNavEvent
    data object OpenPrivacyPolicy : ProfileNavEvent
    data object OpenMyCommunities : ProfileNavEvent
}
