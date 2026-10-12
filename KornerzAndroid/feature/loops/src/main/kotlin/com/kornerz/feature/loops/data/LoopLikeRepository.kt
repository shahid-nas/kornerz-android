package com.kornerz.feature.loops.data

import com.kornerz.core.network.loops.LoopLikeFirestoreApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoopLikeRepository @Inject constructor(
    private val api: LoopLikeFirestoreApi,
) {
    suspend fun likeLoop(userId: String, loopId: String): Result<Unit> =
        api.likeLoop(userId, loopId)

    suspend fun unlikeLoop(userId: String, loopId: String): Result<Unit> =
        api.unlikeLoop(userId, loopId)

    suspend fun checkIfUserLiked(userId: String, loopId: String): Result<Boolean> =
        api.checkIfUserLiked(userId, loopId)
}
