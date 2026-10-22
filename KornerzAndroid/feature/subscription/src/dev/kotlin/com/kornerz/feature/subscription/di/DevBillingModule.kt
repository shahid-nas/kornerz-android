package com.kornerz.feature.subscription.di

import com.kornerz.feature.subscription.data.billing.SubscriptionBillingGateway
import com.kornerz.feature.subscription.data.billing.dev.DevBillingGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The `dev` binding for the billing seam.
 *
 * Lives in `src/dev/kotlin`, so it is not even compiled into a `prod`
 * variant — there is no build flag, manifest marker, or remote config that
 * could activate the simulated gateway in a shipped build.
 *
 * The counterpart is [com.kornerz.feature.subscription.di.ProdBillingModule]
 * in `src/prod/kotlin`, which binds the real `BillingClientApi`. Every
 * consumer — `SubscriptionRepository`, `SubscriptionViewModel`, and the
 * paywall gate — depends on [SubscriptionBillingGateway] and is therefore
 * flavor-agnostic.
 *
 * Note this is the one `@Provides` that breaks the module's usual
 * "constructor-injected, marker-object DI" convention, and the one
 * `@Binds` the `prod` module needs. Both earn their keep for the same
 * reason `NetworkModule`'s `AuthApi` binding does: they are the seam that
 * keeps a Play dependency out of everything above it.
 */
@Module
@InstallIn(SingletonComponent::class)
object DevBillingModule {

    @Provides
    @Singleton
    fun provideSubscriptionBillingGateway(
        gateway: DevBillingGateway,
    ): SubscriptionBillingGateway = gateway
}