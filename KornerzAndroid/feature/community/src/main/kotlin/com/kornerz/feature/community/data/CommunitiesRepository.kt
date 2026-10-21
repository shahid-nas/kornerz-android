package com.kornerz.feature.community.data

import com.kornerz.core.model.Community
import com.kornerz.core.model.CommunityAdmin
import com.kornerz.feature.community.data.network.CommunitiesFirestoreApi
import com.kornerz.feature.community.data.network.CommunityMembersFirestoreApi
import com.kornerz.feature.community.data.network.NooksFirestoreApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which count to fetch for a community. Used by [CommunitiesRepository.
 * fetchCount] to dispatch to the right Firestore collection.
 */
enum class CommunityCountKind { Members, Nooks }

/**
 * Repository for the `Communities/{idCommunity}` collection. Wraps the
 * Firestore APIs and exposes the read operations the admin list
 * needs (P7, task 4.1). The P7 "create community" write surface has
 * been removed after the iOS audit — iOS does not surface a Create
 * Community flow anywhere in the app.
 *
 * Mirrors the read surface of iOS `CommunitiesRepository.swift`.
 */
@Singleton
class CommunitiesRepository @Inject constructor(
    private val communitiesApi: CommunitiesFirestoreApi,
    private val membersApi: CommunityMembersFirestoreApi,
    private val nooksApi: NooksFirestoreApi,
) {

    /**
     * Returns the list of communities created by [userCode], sorted
     * by `createdDate` descending. Each entry is projected into a
     * [CommunityAdmin] with its live member + nook counts (the two
     * count queries run in parallel).
     */
    suspend fun fetchOwnedByUser(userCode: String): Result<List<CommunityAdmin>> =
        runCatching {
            val communities = communitiesApi.fetchOwnedByUser(userCode).getOrThrow()
            communities.map { community ->
                val members = membersApi.fetchCount(community.code)
                val nooks = nooksApi.fetchCountForCommunity(community.code)
                CommunityAdmin.from(community, members, nooks)
            }
        }

    /**
     * Returns a single community by [code], or `null` if missing.
     */
    suspend fun fetchByCode(code: String): Community? =
        communitiesApi.fetchByCode(code)

    /**
     * Returns the live count for the requested [kind] of a community.
     */
    suspend fun fetchCount(code: String, kind: CommunityCountKind): Int =
        when (kind) {
            CommunityCountKind.Members -> membersApi.fetchCount(code)
            CommunityCountKind.Nooks -> nooksApi.fetchCountForCommunity(code)
        }
}
