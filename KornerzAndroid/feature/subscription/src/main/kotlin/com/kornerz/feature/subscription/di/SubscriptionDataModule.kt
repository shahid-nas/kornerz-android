package com.kornerz.feature.subscription.di

import com.kornerz.feature.subscription.data.SubscriptionRepository
import com.kornerz.feature.subscription.data.billing.BillingClientApi
import com.kornerz.feature.subscription.data.network.SubscriptionCloudFunctionsApi
import com.kornerz.feature.subscription.data.network.SubscriptionFirestoreApi
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for the `feature/subscription` data layer.
 *
 * All four types are already `@Singleton`-annotated and
 * constructor-injected, so this module exists purely to surface them in
 * the component graph — the convention in 13 of 15 DI files in this
 * codebase (`ProfileDataModule` is the local precedent).
 *
 * **No `@Binds` is declared.** The one binding in the codebase is
 * `NetworkModule`'s for `AuthApi`, whose interface exists so a test
 * double can be swapped in; there is no instrumentation harness today, so
 * `BillingClientApi` stays concrete and construction-injected.
 *
 * `FirebaseFunctions` is already provided by
 * `core/network/.../di/FirebaseModule.kt:39-41`, which is why
 * `SubscriptionCloudFunctionsApi` adds nothing here.
 */
@Module
@InstallIn(SingletonComponent::class)
object SubscriptionDataModule