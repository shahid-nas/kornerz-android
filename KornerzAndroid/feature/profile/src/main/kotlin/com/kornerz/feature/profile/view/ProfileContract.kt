package com.kornerz.feature.profile.view

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.User

/**
 * UI state for the bottom-nav Profile tab (P6, screen 7.1).
 */
data class ProfileState(
    val user: AsyncValue<User> = AsyncValue.Loading,
    val adminCommunitiesCount: Int = 0,
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
    /**
     * Emitted by the "Premium" menu row. The shell collector routes it to
     * `SubscriptionRoute.Plans`.
     *
     * **Android-only row with no iOS parity target:**
     * `UserProfileVC.setupMenuList()`
     * (`kornerz/Kornerz/HomeVC/ProfileVC/UserProfileVC.swift:75-110`)
     * inserts no premium cell and `TypeOfUserInfoCell` (`:11-22`) has no
     * premium case — iOS reaches the paywall only through two automatic
     * gates. The row is still required, because without it a free user who
     * dismisses the automatic paywall has no way back to the plans screen,
     * which would make the dismissal affordance a dead end.
     */
    data object OpenPremium : ProfileNavEvent
    data object OpenNotifications : ProfileNavEvent
    data object OpenEditBasicInfo : ProfileNavEvent
    data object OpenPrivacyPolicy : ProfileNavEvent
    data object OpenMyCommunities : ProfileNavEvent
    /** Emitted by the "Logout" menu row. The shell collector routes
     *  this to [com.kornerz.feature.shell.ShellViewModel.onLogout] which
     *  clears the FCM token + emits `ShellNavEvent.Logout` (mirrors
     *  iOS `UserProfileVC.setupMenuList` line 100 `logout` row → line
     *  225 logout handler). */
    data object OpenLogout : ProfileNavEvent
    /** Emitted by the "Delete Account" menu row. The shell collector
     *  shows the [com.kornerz.feature.profile.accountdeletion.
     *  AccountDeletionConfirmDialog] (mirrors iOS line 105
     *  `deleteAccount` row → line 249 `deleteAccount` handler). */
    data object OpenDeleteAccount : ProfileNavEvent
}
