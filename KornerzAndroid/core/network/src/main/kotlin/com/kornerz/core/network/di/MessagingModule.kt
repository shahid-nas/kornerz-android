package com.kornerz.core.network.di

import com.google.firebase.messaging.FirebaseMessaging
import com.kornerz.core.network.fcm.FcmTokenRegistrar
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the [FirebaseMessaging] singleton used by the
 * [FcmTokenRegistrar] and the FCM service.
 */
@Module
@InstallIn(SingletonComponent::class)
object MessagingModule {

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()
}