package com.kornerz.core.storage.di

import android.content.Context
import com.kornerz.core.storage.session.SessionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the [SessionRepository] singleton. All session-gated
 * ViewModels and the splash screen inject this; the FCM registrar reads
 * its token synchronously on `Application.onCreate`.
 *
 * Kept in `core/storage/di` so every other module can `@Inject`
 * [SessionRepository] without knowing about DataStore details.
 */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideSessionRepository(
        @ApplicationContext context: Context,
    ): SessionRepository = SessionRepository.create(context)
}