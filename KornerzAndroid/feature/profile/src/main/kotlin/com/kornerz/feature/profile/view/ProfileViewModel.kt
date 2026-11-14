package com.kornerz.feature.profile.view

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.SubscriptionProductType
import com.kornerz.core.model.User
import com.kornerz.core.network.community.CommunitiesRepository
import com.kornerz.core.network.storage.ImageOrientationDecoder
import com.kornerz.feature.profile.data.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.CancellationException
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
 * [CommunitiesRepository.fetchUserCommunitiesCount] flow.
 *
 * P8 fix: switches to `CommunitiesRepository.refreshAdminCount`
 * which counts `CommunityAdm` rows via Firestore's server-side
 * aggregation (`count().get(AggregateSource.SERVER)`), mirroring iOS
 * `AdminCommunitiesRepository.fetchUserCommunitiesCount` (lines
 * 140–159). This is what powers the "My Communities" row visibility
 * on the profile screen.
 *
 * The count is read from the **data layer** rather than from the
 * Community feature: a feature module may not depend on another
 * feature module (`docs/architecture.md` §2.5).
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserProfileRepository,
    private val communitiesRepository: CommunitiesRepository,
    private val imageOrientationDecoder: ImageOrientationDecoder,
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
                    // `refresh` resolves the user by their `codeUser`.
                    // `user.id` is the Firestore document id (the email),
                    // so the two are interchangeable only when the code
                    // is absent — pass whichever the session actually has.
                    val lookupKey = user.userCode.orEmpty().ifEmpty { user.id }
                    userRepository.refresh(lookupKey)
                    observeAdminCommunitiesCount(lookupKey)
                }
            }
        }
    }

    /**
     * Reads the "Communities" count and keeps it in sync.
     *
     * Everything in here runs inside [viewModelScope], whose
     * coroutines are **not** exception-safe: an unhandled throwable
     * escapes to the default uncaught-exception handler and kills the
     * process. The count is read over the network, so it is wrapped in
     * a guard that logs the failure and keeps the screen alive with the
     * last known value.
     */
    private suspend fun observeAdminCommunitiesCount(userCode: String) {
        try {
            communitiesRepository.refreshAdminCount(userCode)
                .onFailure { error ->
                    Log.w(TAG, "refreshAdminCount: ${error.message}")
                }
            communitiesRepository.fetchUserCommunitiesCount(userCode).collect { count ->
                _state.update { it.copy(adminCommunitiesCount = count) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "observeAdminCommunitiesCount: ${e.message}")
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

    fun onPremiumTap() = _navEvents.trySend(ProfileNavEvent.OpenPremium)
    fun onNotificationsTap() = _navEvents.trySend(ProfileNavEvent.OpenNotifications)
    fun onEditBasicInfoTap() = _navEvents.trySend(ProfileNavEvent.OpenEditBasicInfo)
    fun onPrivacyPolicyTap() = _navEvents.trySend(ProfileNavEvent.OpenPrivacyPolicy)
    fun onMyCommunitiesTap() = _navEvents.trySend(ProfileNavEvent.OpenMyCommunities)
    fun onLogoutTap() = _navEvents.trySend(ProfileNavEvent.OpenLogout)
    fun onDeleteAccountTap() = _navEvents.trySend(ProfileNavEvent.OpenDeleteAccount)

    /**
     * The Premium row's trailing label, derived from the **session**
     * [User] already observed above — so it updates whenever the session
     * flow re-emits, with no extra fetch.
     *
     * | Condition | Label |
     * |---|---|
     * | `isUserPremiumNow == false` | `Premium` (the marketing word) |
     * | premium + `video_call_monthly` | `Monthly` |
     * | premium + `video_call_yearly_sub` | `Yearly` |
     * | premium + unrecognized / absent type | `Active until {yyyy-MM-dd}` |
     *
     * The premium decision reads [User.isUserPremiumNow], never the raw
     * `isPremium` bit, which is write-only on both platforms.
     */
    fun premiumTrailingText(): String {
        val user = currentUser() ?: return PREMIUM_LABEL
        if (!user.isUserPremiumNow) return PREMIUM_LABEL
        return when (SubscriptionProductType.fromRawValue(user.subscriptionType)) {
            SubscriptionProductType.MONTHLY -> PLAN_LABEL_MONTHLY
            SubscriptionProductType.YEARLY -> PLAN_LABEL_YEARLY
            // Rendered in the device's own zone: the expiry is an absolute
            // instant, so "until" must read as a local date to the person
            // looking at it.
            null -> user.subscriptionExpiryInstant
                ?.toLocalDateTime(TimeZone.currentSystemDefault())
                ?.date
                ?.let { "$ACTIVE_UNTIL_PREFIX$it" }
                ?: PREMIUM_LABEL
        }
    }

    /**
     * Decodes the picked avatar and uploads it for [user].
     *
     * The picked file carries an EXIF orientation tag that `BitmapFactory`
     * ignores, so the image is normalized before it reaches the JPEG
     * re-encode. Re-encoding without applying the tag would strip the
     * orientation and bake the rotation into the stored image.
     */
    fun uploadAvatar(user: User, uri: Uri) {
        if (_state.value.isUploadingImage) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isUploadingImage = true,
                    uploadProgress = 0.0,
                    uploadStepProgress = 0.0,
                    uploadStep = STEP_UPLOADING_IMAGE,
                    uploadError = null,
                )
            }
            val bitmap = runCatching { imageOrientationDecoder.decode(uri) }
                .getOrElse { error ->
                    finishUpload(null, error)
                    return@launch
                }
            uploadDecodedAvatar(user, bitmap)
        }
    }

    /**
     * Uploads a new avatar for [user].
     *
     * The whole [User] is handed to the repository rather than a bare
     * id: the Storage path needs the `codeUser` field, while the
     * Firestore write needs the user's email. Passing the resolved user
     * keeps those two identifiers from being mixed up.
     */
    private fun uploadDecodedAvatar(user: User, bitmap: android.graphics.Bitmap) {
        viewModelScope.launch {
            val result = userRepository.uploadProfileImage(user, bitmap) { step, progress ->
                _state.update { current ->
                    current.copy(
                        uploadProgress = overallProgress(step, progress),
                        uploadStepProgress = progress,
                        uploadStep = if (step == STEP_THUMBNAIL) {
                            STEP_UPLOADING_THUMBNAIL
                        } else {
                            STEP_UPLOADING_IMAGE
                        },
                    )
                }
            }
            finishUpload(result.getOrNull(), result.exceptionOrNull())
        }
    }

    private fun finishUpload(user: User?, error: Throwable?) {
        _state.update {
            it.copy(
                isUploadingImage = false,
                uploadProgress = if (user != null) 1.0 else it.uploadProgress,
                uploadStepProgress = if (user != null) 1.0 else it.uploadStepProgress,
                user = if (user != null) {
                    AsyncValue.Success(user)
                } else {
                    it.user
                },
                uploadError = error?.message,
            )
        }
        error?.let { failure ->
            Log.w(TAG, "uploadAvatar failed: ${failure.message}")
        }
    }

    /**
     * The overall progress of the two-step avatar upload.
     *
     * The full image is worth 60% and the thumbnail 40%, so the bar
     * reaches the end of each step and then continues into the next
     * one without ever jumping backwards.
     */
    private fun overallProgress(step: Int, progress: Double): Double {
        val clamped = progress.coerceIn(0.0, 1.0)
        return if (step == STEP_THUMBNAIL) {
            FULL_IMAGE_WEIGHT + (clamped * THUMBNAIL_WEIGHT)
        } else {
            clamped * FULL_IMAGE_WEIGHT
        }
    }

    fun currentUser(): User? = (_state.value.user as? AsyncValue.Success)?.data

    private companion object {
        const val TAG = "ProfileViewModel"
        const val PREMIUM_LABEL = "Premium"
        const val PLAN_LABEL_MONTHLY = "Monthly"
        const val PLAN_LABEL_YEARLY = "Yearly"
        const val ACTIVE_UNTIL_PREFIX = "Active until "

        /** Step index of the full-size image upload. */
        const val STEP_FULL_IMAGE = 1

        /** Step index of the thumbnail upload. */
        const val STEP_THUMBNAIL = 2

        /** The share of the overall bar owned by the full-image upload. */
        const val FULL_IMAGE_WEIGHT = 0.6

        /** The share of the overall bar owned by the thumbnail upload. */
        const val THUMBNAIL_WEIGHT = 0.4

        const val STEP_UPLOADING_IMAGE = "Uploading image..."
        const val STEP_UPLOADING_THUMBNAIL = "Uploading thumbnail..."
    }
}
