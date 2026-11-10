package com.kornerz.feature.notifications.di

import com.kornerz.feature.notifications.data.NotificationRepository
import com.kornerz.feature.notifications.data.network.NotificationFirestoreApi
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for the `feature/notifications` data layer. The
 * two types are already `@Singleton`-annotated and constructor-injected,
 * so this module exists to surface them in the component graph
 * (P6, task 4.4).
 *
 * The component is [SingletonComponent]: the FCM service — which runs in
 * the app process and outlives any ViewModel — injects
 * [NotificationRepository] to record the data payloads it receives, and
 * the notifications screen injects the **same** instance to read them.
 * Installing the module in `ViewModelComponent` gave the two callers
 * different instances, so a notification pushed by the service never
 * reached the screen's cache.
 */
@Module
@InstallIn(SingletonComponent::class)
object NotificationsDataModule
