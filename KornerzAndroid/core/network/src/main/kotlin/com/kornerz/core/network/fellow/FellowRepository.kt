package com.kornerz.core.network.fellow

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.kornerz.core.model.FollowMember
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FellowRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    private val collection = firestore.collection(FIELD_COLLECTION)

    suspend fun userFelllowsList(userCode: String): Result<List<FollowMember>> = runCatching {
        if (userCode.isBlank()) return@runCatching emptyList()
        val snapshot = collection
            .whereEqualTo(FIELD_USER_NAME, userCode)
            .get()
            .await()
        snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            FollowMember(
                id = data[FIELD_ID] as? String,
                communityFollowed = data[FIELD_COMMUNITY] as? String,
                userName = data[FIELD_USER_NAME] as? String,
                active = data[FIELD_ACTIVE] as? Boolean,
                dateTime = (data[FIELD_DATE_TIME] as? Timestamp)
                    ?.toInstant()?.let { Instant.fromEpochMilliseconds(it.toEpochMilli()) },
            )
        }
    }

    private companion object {
        const val FIELD_COLLECTION = "Fellow"
        const val FIELD_ID = "fellowId"
        const val FIELD_COMMUNITY = "fellowCommunity"
        const val FIELD_USER_NAME = "fellowUserName"
        const val FIELD_ACTIVE = "fellowAtive"
        const val FIELD_DATE_TIME = "fellowDateTime"
    }
}
