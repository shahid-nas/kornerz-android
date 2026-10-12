package com.kornerz.feature.profile.data

import android.graphics.Bitmap
import com.kornerz.core.model.User
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.feature.profile.data.network.CloudFunctionsApi
import com.kornerz.feature.profile.data.network.UserFirestoreApi
import com.kornerz.feature.profile.data.network.UserNicknameFirestoreApi
import com.kornerz.feature.profile.data.network.UserStorageApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for the signed-in user. Wraps the Firestore + Storage
 * APIs and exposes a single source of truth for the cached [User].
 *
 * Mirrors iOS `AppHelper.shared.user` + `NotificationCenter.post
 * (.profileUpdatedNotification)` pattern (P6, task 3.4).
 *
 * **Document key convention:** all writes target `Users/{email}` —
 * the same convention as the iOS `FireStoreServiceManager` and the
 * Android P1 `FirestoreAuthApi.signUp`. The repository accepts a
 * `userCode` parameter for API symmetry with the iOS source but
 * internally resolves the email from the in-memory user cache.
 */
@Singleton
class UserProfileRepository @Inject constructor(
    private val userApi: UserFirestoreApi,
    private val nicknameApi: UserNicknameFirestoreApi,
    private val storageApi: UserStorageApi,
    private val cloudFunctionsApi: CloudFunctionsApi,
    private val sessionRepository: SessionRepository,
) {

    private val inMemoryUser = MutableStateFlow<User?>(null)

    /**
     * Emits the current cached user. The session repository remains the
     * source of truth for the auth token; the in-memory `MutableStateFlow`
     * lets consumers observe mid-session mutations (name / nickname
     * updates, avatar uploads) without re-reading DataStore.
     */
    val observeCurrentUser: Flow<User?>
        get() = inMemoryUser.asStateFlow()

    /** Stream the persisted session user. */
    fun observeSessionUser(): Flow<User?> =
        sessionRepository.data.map { state ->
            when (state) {
                is SessionState.Authenticated -> state.user
                else -> null
            }
        }.distinctUntilChanged()

    suspend fun refresh(userCode: String): Result<User?> = runCatching {
        val user = userApi.fetchByCode(userCode)
        inMemoryUser.value = user
        if (user != null) {
            // Mirror iOS `FireStoreServiceManager.updateRegistrationDateIfNeeded`
            // (lines 57–76) — stamp the seed date if missing.
            userApi.updateRegistrationDateIfNeeded(user.email)
        }
        user
    }

    suspend fun updateName(userCode: String, newName: String): Result<User> = runCatching {
        val email = requireEmailForUserCode(userCode)
        userApi.updateName(email, newName).getOrThrow()
        val updated = inMemoryUser.value?.updateName(newName)
            ?: userApi.fetchByEmail(email)
        inMemoryUser.value = updated
        updated ?: error("User $email not found after update")
    }

    suspend fun updateNickName(userCode: String, newNickName: String): Result<User> = runCatching {
        val email = requireEmailForUserCode(userCode)
        userApi.updateNickName(email, newNickName).getOrThrow()
        val updated = inMemoryUser.value?.updateNickName(newNickName)
            ?: userApi.fetchByEmail(email)
        inMemoryUser.value = updated
        updated ?: error("User $email not found after update")
    }

    suspend fun checkIfNickNameExists(nickName: String): Result<Boolean> =
        nicknameApi.checkIfNickNameExists(nickName)

    /**
     * Mirrors iOS `UserProfileRepository.uploadUserProfileImage` (P6,
     * `phase-6-profile-and-notifications` task 3.4):
     *  1. Compress [bitmap] to full + 200-pt thumbnail JPEGs.
     *  2. Upload both to `users/{userCode}/profile/avatar{,_thumb}.jpg`.
     *  3. Write **four** fields to `Users/{email}`:
     *     `firebaseImageUrl`, `firebaseThumbnailUrl`, plus the legacy
     *     `image` / `thumbnail` fields (kept populated for the iOS
     *     `User.imageURL` / `User.thumbnailURL` IPFS branch).
     */
    suspend fun uploadProfileImage(userCode: String, bitmap: Bitmap): Result<User> = runCatching {
        val email = requireEmailForUserCode(userCode)
        val jpeg = UserFirestoreApi.bitmapToJpegBytes(bitmap, FULL_QUALITY)
        val thumbnail = UserFirestoreApi.resizeForThumbnail(bitmap)
        val thumbJpeg = UserFirestoreApi.bitmapToJpegBytes(thumbnail, THUMB_QUALITY)
        val (imageUrl, thumbUrl) = storageApi.uploadAvatar(userCode, jpeg, thumbJpeg).getOrThrow()
        userApi.updateProfileImage(
            email = email,
            firebaseImageUrl = imageUrl,
            firebaseThumbnailUrl = thumbUrl,
            legacyImageHash = imageUrl,
            legacyThumbnailHash = thumbUrl,
        ).getOrThrow()
        val current = inMemoryUser.value ?: userApi.fetchByEmail(email)
            ?: error("User $email not found")
        val withImage = current.copy(
            firebaseImageUrl = imageUrl,
            firebaseThumbnailUrl = thumbUrl,
            image = imageUrl,
            thumbnail = thumbUrl,
        )
        inMemoryUser.value = withImage
        withImage
    }

    suspend fun updateFcmToken(userCode: String, token: String): Result<Unit> = runCatching {
        val email = requireEmailForUserCode(userCode)
        userApi.updateFcmToken(email, token)
    }

    suspend fun removeFcmToken(userCode: String): Result<Unit> = runCatching {
        val email = requireEmailForUserCode(userCode)
        userApi.removeFcmToken(email)
    }

    suspend fun deleteAccount(): Result<Unit> = cloudFunctionsApi.deleteUserAccount()

    suspend fun clearLocalSession() = sessionRepository.clearSession()

    /**
     * Resolves a `userCode` to the user's email (the Firestore
     * document key). Falls back to `whereEqualTo("codeUser", ...)`
     * when the in-memory cache doesn't already contain the user.
     */
    private suspend fun requireEmailForUserCode(userCode: String): String {
        val cached = inMemoryUser.value
        if (cached != null && cached.userCode == userCode) return cached.email
        if (cached != null && cached.id == userCode) return cached.email
        val fetched = userApi.fetchByCode(userCode)
            ?: error("User $userCode not found in Firestore")
        inMemoryUser.value = fetched
        return fetched.email
    }

    private companion object {
        const val FULL_QUALITY = 50
        const val THUMB_QUALITY = 30
    }
}
