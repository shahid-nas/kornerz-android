package com.kornerz.core.network.user

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class UserSnippet(
    val code: String,
    val name: String,
    val thumbnailUrl: String?,
)

@Singleton
class UsersRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    suspend fun fetchUsersByCodes(codes: List<String>): Map<String, UserSnippet> {
        if (codes.isEmpty()) return emptyMap()
        val snapshot = firestore.collection(FIELD_USER_COLLECTION)
            .whereIn(FIELD_USER_CODE, codes)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            val code = data[FIELD_USER_CODE] as? String ?: return@mapNotNull null
            val name = data[FIELD_NAME] as? String ?: return@mapNotNull null
            val thumbnail = (data[FIELD_FIREBASE_THUMBNAIL_URL] as? String)
                ?: (data[FIELD_FIREBASE_IMAGE_URL] as? String)
            code to UserSnippet(code = code, name = name, thumbnailUrl = thumbnail)
        }.toMap()
    }

    private companion object {
        const val FIELD_USER_COLLECTION = "Users"
        const val FIELD_USER_CODE = "codeUser"
        const val FIELD_NAME = "name"
        const val FIELD_FIREBASE_THUMBNAIL_URL = "firebaseThumbnailUrl"
        const val FIELD_FIREBASE_IMAGE_URL = "firebaseImageUrl"
    }
}
