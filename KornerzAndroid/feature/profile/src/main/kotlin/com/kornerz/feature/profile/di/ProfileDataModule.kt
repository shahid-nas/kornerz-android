package com.kornerz.feature.profile.di

import com.kornerz.feature.profile.data.UserProfileRepository
import com.kornerz.feature.profile.data.network.UserFirestoreApi
import com.kornerz.feature.profile.data.network.UserNicknameFirestoreApi
import com.kornerz.feature.profile.data.network.UserStorageApi
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/**
 * Hilt module for the `feature/profile` data layer. The four
 * types are already `@Singleton`-annotated and constructor-injected,
 * so this module exists to surface them in the component graph
 * (P6, task 3.5).
 */
@Module
@InstallIn(ViewModelComponent::class)
object ProfileDataModule
