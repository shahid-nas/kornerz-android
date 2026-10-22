package com.kornerz.core.model

/**
 * Documentation-only parity constant. **Never branch on this value.**
 *
 * Mirrors iOS `Constants.trialPeriodInSeconds` = `7 * 24 * 60 * 60`
 * (`kornerz/Kornerz/Helper/Config.swift:117`), which has **zero
 * readers** in the iOS tree: nothing computes a trial end date from it.
 * The "1 week free" wording on both platforms comes from the store's
 * own introductory offer — App Store Connect on iOS
 * (`SubscriptionViewModel.swift:73-79` reads
 * `product.subscription?.introductoryOffer != nil`) and Play's
 * free-trial offer on the base plan on Android, read from
 * `ProductDetails.freeTrialOfferDetails` into
 * [SubscriptionProduct.hasFreeTrialOffer].
 *
 * The entitlement gate is purely `subscriptionExpiryDate > now` on both
 * platforms (`User.isUserPremiumNow`). This constant exists so a reader
 * tracing the iOS source finds the 7-day figure here explicitly labelled
 * as inert, rather than inventing a local timer from it.
 */
const val TRIAL_PERIOD_DAYS: Int = 7