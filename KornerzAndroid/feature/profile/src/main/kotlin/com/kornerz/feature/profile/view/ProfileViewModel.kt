package com.kornerz.feature.profile.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.User
import com.kornerz.feature.community.data.AdminCommunitiesRepository
import com.kornerz.feature.profile.data.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the bottom-nav Profile tab (P6, screen 7.1). Mirrors
 * iOS `UserProfileVC.viewDidLoad` flow (lines 53–65 of
 * `UserProfileVC.swift`).
 *
 * P7 (task 8.1) replaces the hardcoded `adminCommunitiesCount = 0`
 * placeholder with the live
 * [AdminCommunitiesRepository.fetchUserCommunitiesCount] flow.
 *
 * P8 fix: switches to `AdminCommunitiesRepository.refreshAdminCount`
 * which counts `CommunityAdm` rows via Firestore's server-side
 * aggregation (`count().get(AggregateSource.SERVER)`), mirroring iOS
 * `AdminCommunitiesRepository.fetchUserCommunitiesCount` (lines
 * 140–159). This is what powers the "My Communities" row visibility
 * on the profile screen.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserProfileRepository,
    private val adminCommunitiesRepository: AdminCommunitiesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private val _navEvents = Channel<ProfileNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<ProfileNavEvent> = _navEvents.receiveAsFlow()

    init {
        observeUser()
    }

    private fun observeUser() {
        viewModelScope.launch {
            userRepository.observeSessionUser().collect { user ->
                if (user != null && user.id.isNotEmpty()) {
                    _state.update { it.copy(user = AsyncValue.Success(user)) }
                    userRepository.refresh(user.id)
                    val userCode = user.userCode.orEmpty().ifEmpty { user.id }
                    observeAdminCommunitiesCount(userCode)
                }
            }
        }
    }

    private suspend fun observeAdminCommunitiesCount(userCode: String) {
        adminCommunitiesRepository.refreshAdminCount(userCode)
        adminCommunitiesRepository.fetchUserCommunitiesCount(userCode).collect { count ->
            _state.update { it.copy(adminCommunitiesCount = count) }
        }
    }

    fun onEvent(event: ProfileEvent) {
        when (event) {
            is ProfileEvent.UserChanged ->
                _state.update { it.copy(user = AsyncValue.Success(event.user)) }
            is ProfileEvent.CommunityCountChanged ->
                _state.update { it.copy(adminCommunitiesCount = event.count) }
            ProfileEvent.ImageUploadStarted ->
                _state.update { it.copy(isUploadingImage = true) }
            is ProfileEvent.ImageUploadFinished ->
                _state.update { it.copy(isUploadingImage = false) }
            is ProfileEvent.ErrorOccurred -> { /* surfaced via snackbar in screen */ }
        }
    }

    fun onNotificationsTap() = _navEvents.trySend(ProfileNavEvent.OpenNotifications)
    fun onEditBasicInfoTap() = _navEvents.trySend(ProfileNavEvent.OpenEditBasicInfo)
    fun onPrivacyPolicyTap() = _navEvents.trySend(ProfileNavEvent.OpenPrivacyPolicy)
    fun onMyCommunitiesTap() = _navEvents.trySend(ProfileNavEvent.OpenMyCommunities)
    fun onLogoutTap() = _navEvents.trySend(ProfileNavEvent.OpenLogout)
    fun onDeleteAccountTap() = _navEvents.trySend(ProfileNavEvent.OpenDeleteAccount)

    fun uploadAvatar(userCode: String, bitmap: android.graphics.Bitmap) {
        viewModelScope.launch {
            _state.update { it.copy(isUploadingImage = true) }
            val result = userRepository.uploadProfileImage(userCode, bitmap)
            _state.update {
                it.copy(
                    isUploadingImage = false,
                    user = if (result.isSuccess) {
                        AsyncValue.Success(result.getOrThrow())
                    } else it.user,
                )
            }
        }
    }

    fun currentUser(): User? = (_state.value.user as? AsyncValue.Success)?.data
}
