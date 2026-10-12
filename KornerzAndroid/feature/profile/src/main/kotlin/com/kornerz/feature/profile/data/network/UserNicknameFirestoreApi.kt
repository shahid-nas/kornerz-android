package com.kornerz.feature.profile.data.network

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore API for nickname-uniqueness checks. Mirrors iOS
 * `FireStoreServiceManager.checkIfNickNameExist` (P6, task 3.2).
 */
@Singleton
class UserNicknameFirestoreApi @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    /**
     * Returns `true` if a `Users` document with the given [nickName]
     * already exists. Used by the change-nickname flow to enforce
     * uniqueness before writing.
     */
    suspend fun checkIfNickNameExists(nickName: String): Result<Boolean> = runCatching {
        val snapshot = firestore.collection(USERS_COLLECTION)
            .whereEqualTo(FIELD_NICKNAME, nickName)
            .get()
            .await()
        !snapshot.isEmpty
    }

    private companion object {
        const val USERS_COLLECTION = "Users"
        const val FIELD_NICKNAME = "nickname"
    }
}
