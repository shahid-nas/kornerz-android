package com.kornerz.feature.home.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt bindings for the Home graph. `HomeRepository` and
 * `HomeFirestoreApi` are both `@Singleton`-scoped through their
 * `@Inject`-annotated constructors, so this module is currently empty.
 * It exists so future substitutions (test fakes, offline doubles) have
 * a single place to live without touching the constructors.
 */
@Module
@InstallIn(SingletonComponent::class)
object HomeModule
