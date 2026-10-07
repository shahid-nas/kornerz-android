package com.kornerz.core.network.di

import com.google.firebase.messaging.FirebaseMessaging
import com.kornerz.core.network.fcm.FcmTokenRegistrar
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the [FirebaseMessaging] singleton and a no-op handle for the
 * [FcmTokenRegistrar] when the Firebase SDK is unavailable (tests).
 */
@Module
@InstallIn(SingletonComponent::class)
object MessagingModule {

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()
}