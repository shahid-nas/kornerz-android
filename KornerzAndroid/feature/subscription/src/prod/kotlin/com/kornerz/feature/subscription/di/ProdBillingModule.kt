package com.kornerz.feature.subscription.di

import com.kornerz.feature.subscription.data.billing.BillingClientApi
import com.kornerz.feature.subscription.data.billing.SubscriptionBillingGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The `prod` binding for the billing seam: real Google Play Billing.
 *
 * The counterpart is `DevBillingModule` in `src/dev/kotlin`. Neither is
 * visible to the other variant, so a `prod` build cannot construct the
 * simulated gateway even by accident.
 *
 * `BillingClientApi` stays a plain `@Singleton` constructor-injected class —
 * this `@Binds` only names it as the [SubscriptionBillingGateway], it does
 * not become the object's lifecycle owner.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProdBillingModule {

    @Binds
    @Singleton
    abstract fun bindSubscriptionBillingGateway(
        gateway: BillingClientApi,
    ): SubscriptionBillingGateway
}