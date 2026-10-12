package com.kornerz.kornerz.di

import com.kornerz.feature.profile.accountdeletion.UserDeletedHisAccountEventBus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for app-singleton providers that don't have a clean
 * constructor-injection story. P6 (task 9.6) — exposes the
 * `UserDeletedHisAccount` event bus as a singleton.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideUserDeletedHisAccountEventBus(): UserDeletedHisAccountEventBus =
        UserDeletedHisAccountEventBus()
}
