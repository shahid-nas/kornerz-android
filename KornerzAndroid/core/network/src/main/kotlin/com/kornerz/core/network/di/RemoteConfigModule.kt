package com.kornerz.core.network.di

import android.content.Context
import android.content.pm.PackageManager
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.kornerz.core.network.forceupdate.ForceUpdateRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the [FirebaseRemoteConfig] singleton and binds the
 * [ForceUpdateRepository] for the splash screen to consume.
 */
@Module
@InstallIn(SingletonComponent::class)
object RemoteConfigModule {

    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    @Provides
    @Singleton
    fun provideForceUpdateRepository(
        remoteConfig: FirebaseRemoteConfig,
        @ApplicationContext context: Context,
    ): ForceUpdateRepository {
        val versionCode = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
                .longVersionCode.toInt()
        }.getOrDefault(0)
        return ForceUpdateRepository(
            remoteConfig = remoteConfig,
            currentVersionCode = versionCode,
        )
    }
}