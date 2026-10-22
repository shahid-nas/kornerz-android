package com.kornerz.kornerz.di

import com.kornerz.feature.shell.PlayEntitlementChecker
import com.kornerz.feature.shell.SubscriptionEntitlementReconciler
import com.kornerz.feature.subscription.data.SubscriptionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter from [SubscriptionRepository] to the two shell-side seams the
 * paywall gate needs.
 *
 * `feature/shell` owns [PremiumGate] and its two collaborators, while
 * `feature/subscription` owns the Play wrapper and the repository — and
 * `docs/architecture.md` §2.5 forbids one feature module from depending on
 * another (only `app` imports both). This is the composition root's job,
 * so the bindings live here.
 *
 * Both adapters swallow failures by design. A paywall is the wrong place
 * to surface an exception, and worse, a Play or backend outage must never
 * cost a paying subscriber their premium state — see the `premium-gate`
 * and `subscription-verification` specs.
 */
@Singleton
class AndroidPlayEntitlementChecker @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
) : PlayEntitlementChecker {

    override suspend fun hasActiveSubscription(): Boolean =
        subscriptionRepository.queryPurchases().getOrNull()?.isNotEmpty() == true
}

@Singleton
class AndroidEntitlementReconciler @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
) : SubscriptionEntitlementReconciler {

    override suspend fun reconcile() {
        subscriptionRepository.reconcileEntitlement()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object SubscriptionBindingsModule {

    @Provides
    @Singleton
    fun providePlayEntitlementChecker(
        checker: AndroidPlayEntitlementChecker,
    ): PlayEntitlementChecker = checker

    @Provides
    @Singleton
    fun provideSubscriptionEntitlementReconciler(
        reconciler: AndroidEntitlementReconciler,
    ): SubscriptionEntitlementReconciler = reconciler
}