package com.kornerz.core.network.fellow

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.kornerz.core.model.FollowMember
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The `Fellow` collection — the follow graph between a user
 * (`codeUser`) and a community.
 *
 * iOS source-of-truth: `kornerz/Kornerz/Backend/Repositories/FellowRepository.swift`
 * (the SwiftUI repository the Korner detail screen uses) and
 * `kornerz/Kornerz/FirestoreService/APICommunity.swift` (the legacy
 * UIKit repository — same document shape, different write path).
 *
 * The document shape is a `FollowMember`
 * (`kornerz/Kornerz/Models/FollowMember.swift`):
 *
 * | Firestore field | value |
 * |---|---|
 * | `fellowId` | the document id — a random 8-character code |
 * | `fellowCommunity` | the community the user follows |
 * | `fellowUserName` | the following user's `codeUser` |
 * | `fellowAtive` | `true` (the backend's spelling — kept verbatim) |
 *
 * Every field name below — including the backend's `fellowAtive`
 * misspelling — is the iOS spelling, so a document written by Android
 * is byte-identical to one written by iOS.
 */
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

    /**
     * The `codeUser` of every fellow following any of [communities] —
     * the raw half of the community's member list.
     *
     * Mirrors iOS `FellowRepository.fetchCommunityFellows(community:)`
     * (`kornerz/Kornerz/Backend/Repositories/FellowRepository.swift:88`),
     * which matches the community by `communityId` + `communityName` +
     * `communityNameEn`.
     */
    suspend fun fetchCommunityFollowerCodes(
        communities: List<String>,
        limit: Long,
    ): Result<List<String>> =
        runCatching {
            val keys = communities.filter { it.isNotBlank() }.distinct()
            if (keys.isEmpty()) return@runCatching emptyList()
            val snapshot = collection
                .whereIn(FIELD_COMMUNITY, keys)
                .limit(limit)
                .get()
                .await()
            snapshot.documents
                .mapNotNull { it.data?.get(FIELD_USER_NAME) as? String }
                .filter { it.isNotBlank() }
        }

    /**
     * Follows a community.
     *
     * Mirrors iOS `FellowRepository.followCommunity(userId:community:)`
     * (`kornerz/Kornerz/Backend/Repositories/FellowRepository.swift:119`),
     * which the Korner detail screen calls from
     * `KornerDetailViewModel.followKorner()`.
     *
     * The iOS call is:
     *
     * ```swift
     * let fellow = FollowMember.from(userId: userId, communityName: community)
     * try collection.document(fellow.id!).setData(from: fellow)
     * ```
     *
     * so the Android port is deliberately identical:
     *
     *  - the document id is a **random 8-character code** — iOS
     *    `AppHelper.getCode(length: 8)` over
     *    `ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789`,
     *  - the same code is also written into the `fellowId` field
     *    (iOS encodes `FollowMember.id` under that key),
     *  - the write is a plain `set` — **no merge**, **no**
     *    `serverTimestamp`, **no** client timestamp,
     *  - the body carries exactly `fellowId`, `fellowCommunity`,
     *    `fellowUserName` and `fellowAtive`. iOS's
     *    `FollowMember.from(...)` leaves `fellowDateTime` `nil`, and
     *    Swift's synthesized `Codable` omits `nil` optionals, so the
     *    document has no `fellowDateTime` field either.
     *
     * [community] is the community's canonical name — the value both
     * platforms resolve a followed community by ("Your Kornerz" reads
     * `Communities.whereField("communityName", in: fellows' fellowCommunity)`).
     */
    suspend fun followCommunity(userCode: String, community: String): Result<Unit> = runCatching {
        if (userCode.isBlank()) error("userCode must not be blank")
        if (community.isBlank()) error("community must not be blank")
        val fellowId = generateFellowId()
        val payload = mapOf(
            FIELD_ID to fellowId,
            FIELD_COMMUNITY to community,
            FIELD_USER_NAME to userCode,
            FIELD_ACTIVE to true,
        )
        // A plain `set` — overwrite, not merge. Mirrors iOS `setData(from:)`.
        collection.document(fellowId).set(payload).await()
    }

    /**
     * Unfollows a community.
     *
     * Mirrors iOS `FellowRepository.unfollowCommunity(userId:community:)`
     * (`kornerz/Kornerz/Backend/Repositories/FellowRepository.swift:139`):
     * a `Fellow` query on `fellowCommunity` + `fellowUserName`, then a
     * delete of the match.
     *
     * Two deliberate refinements over iOS, both required for the state
     * to actually clear:
     *
     *  - iOS matches `fellowCommunity` with a single `isEqualTo`. The
     *    same community is addressable by more than one key — its
     *    `communityId`, its `communityName` and its `communityNameEn` —
     *    and iOS's own two follow paths disagree about which one they
     *    write (the SwiftUI Korner detail screen writes `nameEn`, the
     *    legacy community screen writes `name`). Android therefore
     *    matches the community by **any** of its keys, so a follow
     *    written on either platform is removed.
     *  - iOS deletes only the **first** match. A user who followed
     *    from both platforms holds two documents, and deleting one
     *    leaves the user "following" — the read is a `whereIn`, so the
     *    surviving document still matches. Android deletes **every**
     *    match.
     */
    suspend fun unfollowCommunity(
        userCode: String,
        communities: List<String>,
    ): Result<Unit> = runCatching {
        if (userCode.isBlank()) error("userCode must not be blank")
        val keys = communities.filter { it.isNotBlank() }.distinct()
        if (keys.isEmpty()) error("communities must not be blank")
        val snapshot = collection
            .whereIn(FIELD_COMMUNITY, keys)
            .whereEqualTo(FIELD_USER_NAME, userCode)
            .get()
            .await()
        snapshot.documents.forEach { doc -> doc.reference.delete().await() }
    }

    /**
     * Whether [userCode] follows any of [communities].
     *
     * Mirrors iOS `FellowRepository.fellowExists(userId:communityId:communityName:)`
     * (`kornerz/Kornerz/Backend/Repositories/FellowRepository.swift:72`),
     * which the Korner detail screen calls from
     * `checkIfFellowExists` to drive the Follow / Unfollow label.
     *
     * iOS matches the community by `communityId` + `communityName`; the
     * Android port accepts the community's full key set
     * (`communityId`, `communityName`, `communityNameEn`) — the same set
     * Android's "Your Kornerz" and community-members reads already use,
     * so a follow written by either platform is recognised.
     */
    suspend fun fellowExists(
        userCode: String,
        communities: List<String>,
    ): Result<Boolean> = runCatching {
        if (userCode.isBlank()) return@runCatching false
        val keys = communities.filter { it.isNotBlank() }.distinct()
        if (keys.isEmpty()) return@runCatching false
        val snapshot = collection
            .whereIn(FIELD_COMMUNITY, keys)
            .whereEqualTo(FIELD_USER_NAME, userCode)
            .limit(1)
            .get()
            .await()
        snapshot.documents.isNotEmpty()
    }

    /**
     * The random 8-character code iOS uses as a `Fellow` document id —
     * `AppHelper.getCode(length: 8)` over
     * `ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789`
     * (`kornerz/Kornerz/Helper/AppHelper.swift:207`).
     */
    private fun generateFellowId(): String =
        (1..FELLOW_ID_LENGTH).map { FELLOW_ID_ALPHABET.random() }.joinToString("")

    private companion object {
        const val FIELD_COLLECTION = "Fellow"
        const val FIELD_ID = "fellowId"
        const val FIELD_COMMUNITY = "fellowCommunity"
        const val FIELD_USER_NAME = "fellowUserName"
        const val FIELD_ACTIVE = "fellowAtive"
        const val FIELD_DATE_TIME = "fellowDateTime"

        const val FELLOW_ID_LENGTH = 8
        const val FELLOW_ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    }
}
