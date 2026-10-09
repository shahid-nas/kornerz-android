package com.kornerz.feature.kornerz.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for the Kornerz feature. The [com.kornerz.feature.kornerz.KornerzRepository]
 * is `@Inject constructor` so Hilt provides it automatically — this
 * module is intentionally empty and reserved for future bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
object KornerzModule
