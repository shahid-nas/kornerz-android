package com.kornerz.feature.loops.di

import com.kornerz.core.network.loops.CommentLikeFirestoreApi
import com.kornerz.core.network.loops.LoopCommentFirestoreApi
import com.kornerz.core.network.loops.LoopFirestoreApi
import com.kornerz.core.network.loops.LoopLikeFirestoreApi
import com.kornerz.feature.loops.data.CommentLikeRepository
import com.kornerz.feature.loops.data.LoopCommentRepository
import com.kornerz.feature.loops.data.LoopLikeRepository
import com.kornerz.feature.loops.data.LoopRepository
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for the Loops vertical. The Firestore API wrappers come
 * pre-bound with `@Inject constructor` (Singleton scope); the
 * repositories wrap them. All bindings are `Provides` no-ops because
 * each class already has the right constructor.
 *
 * `feature/home` consumes `LoopRepository` for the recent-loops
 * carousel — the same Singleton instance is shared via Hilt.
 */
@Module
@InstallIn(SingletonComponent::class)
object LoopsModule {
    // The classes above already have @Inject constructor + @Singleton
    // annotations, so Hilt resolves them automatically. This module is
    // a placeholder for any future non-@Inject bindings (e.g., a
    // custom OkHttp client for the loops feed).
}
