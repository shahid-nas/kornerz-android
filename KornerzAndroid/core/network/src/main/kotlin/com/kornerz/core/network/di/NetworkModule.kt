package com.kornerz.core.network.di

import com.kornerz.core.network.auth.AuthApi
import com.kornerz.core.network.auth.FirestoreAuthApi
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the [FirestoreAuthApi] implementation as the singleton [AuthApi]
 * used by [com.kornerz.core.network.AuthRepository]. Tests can replace
 * this binding with a fake `AuthApi` for in-memory ViewModel specs.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindAuthApi(impl: FirestoreAuthApi): AuthApi
}