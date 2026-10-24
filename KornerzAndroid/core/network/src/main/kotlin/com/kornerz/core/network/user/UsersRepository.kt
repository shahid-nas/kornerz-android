package com.kornerz.core.network.user

import com.google.firebase.firestore.FirebaseFirestore
import com.kornerz.core.model.User
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

    /**
     * Resolves the **snippet** of every user whose `codeUser` is in
     * [codes] — the lightweight projection the feeds need (a display
     * name and an avatar URL).
     */
    suspend fun fetchUserSnippetsByCodes(codes: List<String>): Map<String, UserSnippet> {
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

    /**
     * Resolves the **full** `Users` documents whose `codeUser` is in
     * [codes], keyed by `codeUser`.
     *
     * The loop detail screen needs the whole user — the nickname, the
     * avatar URL and the subscription fields — not the snippet the
     * feeds use, so the two projections live side by side.
     *
     * The lookup is **batched**: one `whereIn("codeUser", …)` query per
     * chunk of at most [MAX_IN_QUERY_VALUES] ids, because Firestore
     * caps an `in` clause at a bounded number of values. A 40-comment
     * loop therefore costs two reads instead of 40.
     *
     * Returns `Result.failure` rather than throwing.
     */
    suspend fun fetchUsersByCodes(codes: List<String>): Result<Map<String, User>> = runCatching {
        if (codes.isEmpty()) return@runCatching emptyMap()
        val users = LinkedHashMap<String, User>()
        for (chunk in codes.distinct().chunked(MAX_IN_QUERY_VALUES)) {
            val snapshot = firestore.collection(FIELD_USER_COLLECTION)
                .whereIn(FIELD_USER_CODE, chunk)
                .get()
                .await()
            for (doc in snapshot.documents) {
                val data = doc.data ?: continue
                val code = data[FIELD_USER_CODE] as? String ?: continue
                users[code] = data.toUser(doc.id)
            }
        }
        users
    }

    private fun Map<String, Any?>.toUser(documentId: String): User = User(
        id = documentId,
        email = this[FIELD_EMAIL] as? String ?: "",
        displayName = this[FIELD_NAME] as? String ?: "",
        nickname = this[FIELD_NICKNAME] as? String ?: "",
        userCode = this[FIELD_USER_CODE] as? String,
        phone = this[FIELD_PHONE] as? String,
        countryCode = this[FIELD_COUNTRY_CODE] as? String,
        language = this[FIELD_LANGUAGE] as? String,
        photoUrl = this[FIELD_PHOTO_URL] as? String,
        firebaseImageUrl = this[FIELD_FIREBASE_IMAGE_URL] as? String,
        firebaseThumbnailUrl = this[FIELD_FIREBASE_THUMBNAIL_URL] as? String,
        image = this[FIELD_IMAGE] as? String,
        thumbnail = this[FIELD_THUMBNAIL] as? String,
        thumbnailType = this[FIELD_THUMBNAIL_TYPE] as? String,
    )

    private companion object {
        const val FIELD_USER_COLLECTION = "Users"
        const val FIELD_USER_CODE = "codeUser"
        const val FIELD_NAME = "name"
        const val FIELD_NICKNAME = "nickname"
        const val FIELD_EMAIL = "email"
        const val FIELD_PHONE = "phone"
        const val FIELD_COUNTRY_CODE = "countryCode"
        const val FIELD_LANGUAGE = "language"
        const val FIELD_PHOTO_URL = "photoUrl"
        const val FIELD_FIREBASE_THUMBNAIL_URL = "firebaseThumbnailUrl"
        const val FIELD_FIREBASE_IMAGE_URL = "firebaseImageUrl"
        const val FIELD_IMAGE = "image"
        const val FIELD_THUMBNAIL = "thumbnail"
        const val FIELD_THUMBNAIL_TYPE = "thumbnailType"

        /**
         * Firestore's cap on the number of values in an `in` clause.
         * The ids are chunked into groups of this size.
         */
        const val MAX_IN_QUERY_VALUES = 30
    }
}
