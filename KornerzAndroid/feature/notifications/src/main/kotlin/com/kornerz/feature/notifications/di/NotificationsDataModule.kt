package com.kornerz.feature.notifications.di

import com.kornerz.feature.notifications.data.NotificationRepository
import com.kornerz.feature.notifications.data.network.NotificationFirestoreApi
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/**
 * Hilt module for the `feature/notifications` data layer. The
 * two types are already `@Singleton`-annotated and constructor-injected,
 * so this module exists to surface them in the component graph
 * (P6, task 4.4).
 */
@Module
@InstallIn(ViewModelComponent::class)
object NotificationsDataModule
