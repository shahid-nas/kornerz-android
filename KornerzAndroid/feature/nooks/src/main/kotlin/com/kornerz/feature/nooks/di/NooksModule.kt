package com.kornerz.feature.nooks.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for the Nooks feature. Repository + Firestore API are
 * `@Inject constructor`, so Hilt provides them automatically — this
 * module is intentionally empty and reserved for future bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
object NooksModule
