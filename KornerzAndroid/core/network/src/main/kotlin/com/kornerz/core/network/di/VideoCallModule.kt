package com.kornerz.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Provides shared HTTP / WebSocket infrastructure for the video call
 * pipeline. The `OkHttpClient` is used by
 * [com.kornerz.core.network.videocall.SessionTokenService] to open
 * the WebSocket against `ws://ws.kornerz.com:80/`.
 *
 * `SessionTokenService` and `VonageSessionController` are both
 * `@Inject constructor` `@Singleton`s, so Hilt provides them
 * automatically — this module exists to wire the underlying
 * `OkHttpClient`.
 */
@Module
@InstallIn(SingletonComponent::class)
object VideoCallModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()
}