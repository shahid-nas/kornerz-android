package com.kornerz.feature.community.di

import com.kornerz.feature.community.data.network.CommunitiesFirestoreApi
import com.kornerz.feature.community.data.network.CommunityMembersFirestoreApi
import com.kornerz.feature.community.data.network.NooksFirestoreApi
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for the `feature/community` data layer. The three
 * read-side APIs are already `@Singleton`-annotated and
 * constructor-injected, so this module exists to surface them in the
 * component graph (P7, task 3.5).
 *
 * The P7 `CommunityStorageApi` (cover upload) was removed after the
 * iOS audit — iOS does not surface a Create Community flow, so the
 * Android port no longer writes a cover image.
 */
@Module
@InstallIn(SingletonComponent::class)
object CommunityDataModule
