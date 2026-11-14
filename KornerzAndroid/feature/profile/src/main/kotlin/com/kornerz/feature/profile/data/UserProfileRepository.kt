package com.kornerz.feature.profile.data

import android.graphics.Bitmap
import com.kornerz.core.model.User
import com.kornerz.core.network.fcm.DeviceIdProvider
import com.kornerz.core.network.fcm.DeviceTokenStore
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
    private val deviceTokenStore: DeviceTokenStore,
    private val deviceIdProvider: DeviceIdProvider,
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

    /**
     * Re-reads the user from Firestore and refreshes the in-memory
     * cache.
     *
     * [userCode] is the `codeUser` field. When the user has no
     * `codeUser` — or the code simply does not resolve — the lookup
     * falls back to the email, which is the authoritative document key
     * on both platforms. The previous behaviour returned `null` in that
     * case and silently left the in-memory cache empty.
     */
    suspend fun refresh(userCode: String): Result<User?> = runCatching {
        val user = userApi.fetchByCode(userCode)
            ?: inMemoryUser.value?.email?.let { userApi.fetchByEmail(it) }
            ?: userApi.fetchByEmail(userCode)
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
     *  2. Upload both to `users/{userCode}/profile/{id}.jpg`.
     *  3. Write the `firebaseImageUrl` and `firebaseThumbnailUrl` fields
     *     to `Users/{email}`.
     *
     * The two identifiers are deliberately kept apart, exactly as iOS
     * does (`UserProfileRepository.swift:43` uses `userCode` for the
     * Storage prefix and `:94` uses `userId` for the Firestore document):
     *
     * - the user's `codeUser` — the Storage path prefix. iOS falls back
     *   to the user id when `codeUser` is absent.
     * - the user's email — the `Users/{email}` document key.
     *
     * Passing the already-resolved [user] in means the caller cannot
     * accidentally hand us the document id where the `codeUser` is
     * expected.
     *
     * The new avatar is also written back into the DataStore session so
     * it survives an app restart — iOS does the equivalent with
     * `UserDefaults.standard.saveUserInfo(user)`.
     *
     * @param onProgress reports the `0.0..1.0` progress of each upload
     *   step. The full image is step one and the thumbnail is step two;
     *   the caller weights them into a single overall bar.
     */
    suspend fun uploadProfileImage(
        user: User,
        bitmap: Bitmap,
        onProgress: (step: Int, progress: Double) -> Unit = { _, _ -> },
    ): Result<User> = runCatching {
        val email = requireEmail(user)
        val storagePrefix = user.userCode.orEmpty().ifEmpty { user.id }
        val jpeg = UserFirestoreApi.bitmapToJpegBytes(bitmap, FULL_QUALITY)
        val thumbnail = UserFirestoreApi.resizeForThumbnail(bitmap)
        val thumbJpeg = UserFirestoreApi.bitmapToJpegBytes(thumbnail, THUMB_QUALITY)
        val (imageUrl, thumbUrl) = storageApi
            .uploadAvatar(storagePrefix, jpeg, thumbJpeg, onProgress)
            .getOrThrow()
        userApi.updateProfileImage(
            email = email,
            firebaseImageUrl = imageUrl,
            firebaseThumbnailUrl = thumbUrl,
        ).getOrThrow()
        val current = inMemoryUser.value ?: userApi.fetchByEmail(email)
            ?: error("User $email not found")
        val withImage = current.copy(
            firebaseImageUrl = imageUrl,
            firebaseThumbnailUrl = thumbUrl,
        )
        inMemoryUser.value = withImage
        // Keep the persisted session in step with the new avatar.
        sessionRepository.updateUser { sessionUser ->
            if (sessionUser.id == withImage.id) {
                sessionUser.copy(
                    firebaseImageUrl = withImage.firebaseImageUrl,
                    firebaseThumbnailUrl = withImage.firebaseThumbnailUrl,
                )
            } else {
                sessionUser
            }
        }
        withImage
    }

    /**
     * Registers the device's FCM token for the signed-in user.
     *
     * The write goes through [DeviceTokenStore] — the same writer the
     * token-rotation handler uses — so the token always lands on
     * `Users/{email}.tokenPush` plus the per-device `devices` subdocument.
     */
    suspend fun updateFcmToken(userCode: String, token: String): Result<Unit> = runCatching {
        val email = requireEmailForUserCode(userCode)
        val deviceId = deviceIdProvider.deviceId()
        deviceTokenStore.upsert(email = email, deviceId = deviceId, token = token).getOrThrow()
    }

    /**
     * Removes the device's FCM token.
     *
     * The `tokenPush` field is only cleared when it still holds this
     * device's token, so signing out of one device does not wipe a token
     * another device on the same account has registered. When [token] is
     * `null` the stored token is used as the comparison value, which
     * still only clears a token that belongs to this device's user
     * document.
     */
    suspend fun removeFcmToken(userCode: String, token: String? = null): Result<Unit> = runCatching {
        val email = requireEmailForUserCode(userCode)
        val deviceId = deviceIdProvider.deviceId()
        deviceTokenStore.clear(email = email, deviceId = deviceId, token = token).getOrThrow()
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

    /**
     * Resolves the Firestore document key (the user's email) for an
     * already-resolved [user].
     *
     * `Users/{email}` is the document-key convention shared by iOS
     * (`FireStoreServiceManager.swift:21`) and Android
     * (`FirestoreAuthApi.signUp`). The email is authoritative; the
     * in-memory cache is only consulted so a user whose document was
     * written under a different key can still be updated.
     */
    private suspend fun requireEmail(user: User): String {
        if (user.email.isNotBlank()) return user.email
        val cached = inMemoryUser.value
        if (cached != null && cached.id == user.id) return cached.email
        val fetched = userApi.fetchByEmail(user.id)
            ?: error("User ${user.id} not found in Firestore")
        inMemoryUser.value = fetched
        return fetched.email
    }

    private companion object {
        const val FULL_QUALITY = 50
        const val THUMB_QUALITY = 30
    }
}
