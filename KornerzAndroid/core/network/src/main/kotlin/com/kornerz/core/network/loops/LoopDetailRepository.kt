package com.kornerz.core.network.loops

import com.kornerz.core.model.Community
import com.kornerz.core.model.Loop
import com.kornerz.core.model.LoopAccentPalette
import com.kornerz.core.model.LoopDetailInfo
import com.kornerz.core.model.User
import com.kornerz.core.network.community.CommunitiesRepository
import com.kornerz.core.network.user.UsersRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a loop id into everything the loop-detail screen renders —
 * the Android counterpart of the iOS `LoopInfo` argument object
 * (`kornerz/Kornerz/SwiftUI/Dashboard/Loops/LoopsTab/LoopsTabViewModel.swift:14-40`).
 *
 * iOS resolves the loop, its owner and its community **before** pushing
 * the screen and hands the bundle to the detail view model. Android
 * resolves the same three pieces inside the detail screen, so the join
 * lives here, next to the Firestore APIs, per the layering in
 * `docs/architecture.md` §1.
 *
 * The join is three reads, in order:
 *  1. the `Loop` document, resolved by its `id` **field** with a
 *     document-id fallback (see [LoopFirestoreApi.fetchLoopDetail]);
 *  2. the owner's `Users` document (`codeUser in [publisherId]`);
 *  3. the parent `Communities` document (`communityId in [communityId]`).
 *
 * Every method returns a `Result` and never throws.
 */
@Singleton
class LoopDetailRepository @Inject constructor(
    private val loopFirestoreApi: LoopFirestoreApi,
    private val usersRepository: UsersRepository,
    private val communitiesRepository: CommunitiesRepository,
) {

    /**
     * Resolves [loopId] into a [LoopDetailInfo].
     *
     * An unresolved owner or community degrades to `null` rather than
     * failing the whole read — the title row simply omits the nickname
     * or the community name. A loop that does not exist produces
     * `Result.failure`.
     */
    suspend fun fetchLoopDetail(loopId: String): Result<LoopDetailInfo> = runCatching {
        if (loopId.isBlank()) error("loopId must not be blank")

        val loop = loopFirestoreApi.fetchLoopDetail(loopId).first().getOrThrow()
        val owner = resolveOwner(loop)
        val community = resolveCommunity(loop)

        LoopDetailInfo(
            loop = loop,
            owner = owner,
            community = community,
            accentColorHex = LoopAccentPalette.hexForLoopId(loop.id.orEmpty()),
        )
    }

    /**
     * The `Users` document whose `codeUser` matches the loop's
     * `publisherId`, or `null` when it does not resolve.
     */
    private suspend fun resolveOwner(loop: Loop): User? {
        val publisherId = loop.publisherId?.takeIf { it.isNotBlank() } ?: return null
        return usersRepository.fetchUsersByCodes(listOf(publisherId)).getOrNull()?.get(publisherId)
    }

    /**
     * The `Communities` document whose `communityId` matches the loop's
     * `communityId`, or `null` when it does not resolve.
     */
    private suspend fun resolveCommunity(loop: Loop): Community? {
        val communityId = loop.communityId?.takeIf { it.isNotBlank() } ?: return null
        return communitiesRepository.fetchByCommunityId(communityId)
    }
}
