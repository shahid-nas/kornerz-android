package com.kornerz.feature.subscription.data

/**
 * The value written to `Users/{email}.subscriptionPlatform` when this build
 * grants an entitlement. Mirrors iOS's `"iOS"` (`UserRepository.swift:174`),
 * so a user who subscribed on either platform reads as premium on the
 * other — both derive premium from `subscriptionExpiryDate` and never
 * branch on this field.
 *
 * The `dev` variant resolves the same symbol to `"AndroidDev"` so
 * simulated entitlements stay identifiable in the shared backend; see the
 * `dev` copy of this file.
 */
internal const val ENTITLEMENT_PLATFORM = "Android"