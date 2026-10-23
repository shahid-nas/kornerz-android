# subscription-verification Specification

## Purpose
TBD - created by archiving change phase-8-premium-iap. Update Purpose after archive.
## Requirements
### Requirement: Server-side purchase verification client
`SubscriptionCloudFunctionsApi` (`@Singleton`, `feature/subscription/data/network/`) SHALL expose:

```kotlin
suspend fun verifyPurchase(token: String, productId: String): Result<SubscriptionVerificationResult>
```

It SHALL call the `verifySubscription` HTTPS-callable via `FirebaseFunctions.getHttpsCallable("verifySubscription").call(mapOf("token" to token, "productId" to productId))`, bridged with `kotlinx.coroutines.tasks.await`.

This mirrors the existing `feature/profile/.../data/network/CloudFunctionsApi.kt` (P6, `deleteUserAccount`) exactly: same class shape, same `runCatching { … await(); … }` body, same `Result<Unit>`-style return, same `@Singleton` constructor injection, same `@InstallIn(SingletonComponent::class)` context. `FirebaseFunctions` is already provided as a singleton by `core/network/.../di/FirebaseModule.kt:39-41`, so **no new Hilt provider is required**.

`SubscriptionVerificationResult` SHALL be a `@Serializable` DTO carrying the server's authoritative verdict:

| Field | Type | Meaning |
|---|---|---|
| `expiryDate` | `String?` | ISO-8601 instant from the Google Play Developer API's `expiryTimeMillis` |
| `isActive` | `Boolean` | Server's verdict on whether the entitlement is currently valid |
| `autoRenewing` | `Boolean?` | `autoRenewing` from the Play subscription record |
| `acknowledged` | `Boolean?` | Play's acknowledgement state for the token |

#### Scenario: Verification succeeds
- **WHEN** `verifyPurchase` is called with a valid purchase token and product id
- **THEN** the Google Play Developer API returns a subscription record and the function returns `Result.success` with the populated `SubscriptionVerificationResult`

#### Scenario: Function is not deployed
- **WHEN** the `verifySubscription` callable does not exist in the Firebase project
- **THEN** `verifyPurchase` returns `Result.failure` and no exception escapes the API boundary

### Requirement: The Cloud Function is external to this repository
P8 SHALL author no Cloud Function source and SHALL add no backend function file to this repository. The `verifySubscription` function, its Google Play service-account credentials, and the RTDN Pub/Sub → function subscription are deployed outside this repository alongside the existing iOS infrastructure, exactly as P6's `deleteUserAccount` callable is.

P8 SHALL author **no** function source. The `subscription-verification` capability specifies the callable's contract — request shape and response DTO — so the function can be implemented against a written specification. For context, this repository has no `functions/` directory, no `*.ts` or `*.js` backend source, no `firebase.json`, and no `package.json` outside the iOS Pods tree.

#### Scenario: No function source is authored
- **WHEN** the P8 diff is reviewed
- **THEN** it adds no backend function source, no `firebase.json`, and no service-account credential file to this repository

#### Scenario: The contract is sufficient to implement the function
- **WHEN** someone implements `verifySubscription` against this spec
- **THEN** the request keys (`token`, `productId`) and the response shape (`SubscriptionVerificationResult`) are fully specified without reference to the Android codebase

### Requirement: Entitlement is granted optimistically and reconciled server-side
A locally-verified purchase SHALL grant entitlement **immediately** on the client, before any server round trip completes. This mirrors iOS, where `InAppManager.checkVerified` (`kornerz/Kornerz/InAppPurchase/InAppManager.swift:91-101`) performs on-device JWS verification only and there is no server-side verification anywhere in the iOS tree — no App Store Server API call, no receipt-validation endpoint.

The Android port adds the server round trip that `docs/phases.md:260-261` requires, as an **addition** to iOS's behavior:

1. On a successful purchase, the client acknowledges, writes the entitlement locally from the `Purchase` object's purchase time plus the product's billing period, and navigates to the success screen.
2. In parallel, the client calls `SubscriptionCloudFunctionsApi.verifyPurchase`. On `Result.success`, the authoritative result is written back to `Users/{email}` via `SubscriptionFirestoreApi.setSubscription`, overriding the locally-derived values.
3. On `Result.failure`, the locally-derived entitlement **stands** and no error is surfaced to the user.

The success screen SHALL NOT block on the verification result. A purchase that succeeded locally but whose server round trip failed SHALL still show "Subscription activated successfully".

#### Scenario: Purchase with a working function
- **WHEN** a purchase completes and `verifyPurchase` returns an active verdict with a server expiry
- **THEN** the client navigates to the success screen immediately, and the server's expiry overwrites the locally-derived one when the response arrives

#### Scenario: Purchase with an unavailable function
- **WHEN** a purchase completes and `verifyPurchase` returns `Result.failure`
- **THEN** the client navigates to the success screen, the locally-derived entitlement stands, and no error is surfaced

#### Scenario: Success screen never blocks on the round trip
- **WHEN** `verifyPurchase` has not returned by the time the purchase flow completes
- **THEN** `SubscriptionNavEvent.PurchaseSucceeded` has already been emitted and the success screen is displayed

### Requirement: Reconcile on every app launch
`SubscriptionRepository.reconcileEntitlement()` SHALL be called once per app launch, before the premium gate is evaluated. It SHALL:

1. Read the current session user's email and cached `User`.
2. If `isUserPremiumNow` is `false` and no local Play purchase exists, return without calling the function.
3. Otherwise call `SubscriptionCloudFunctionsApi.verifyPurchase` with the most recent local purchase token, and on `Result.success` with `isActive == false`, call `SubscriptionFirestoreApi.clearSubscription(email)` so the entitlement is revoked locally and server-side.
4. On `Result.failure`, leave the local state untouched.

This is the mechanism that makes the `docs/project-brief.md:264` risk ("Google Play RTDN can be delayed; need graceful fall-back for users who purchase but the server doesn't see it for minutes") manageable: a delayed RTDN is corrected on the next launch rather than persisting indefinitely.

#### Scenario: No entitlement, nothing to reconcile
- **WHEN** the session user is not premium and the device holds no active Play purchase
- **THEN** `reconcileEntitlement()` returns without issuing a network call

#### Scenario: Revoked entitlement is cleared on launch
- **WHEN** the local state says premium but `verifyPurchase` returns `isActive == false`
- **THEN** `clearSubscription` is called and `isUserPremiumNow` evaluates to `false` for the rest of the session

#### Scenario: Reconciliation failure is non-destructive
- **WHEN** `verifyPurchase` returns `Result.failure` during launch reconciliation
- **THEN** the local entitlement state is left untouched and the app proceeds to the gate

### Requirement: RTDN reconciliation contract
The `verifySubscription` callable SHALL be the single reconciliation point for Real-Time Developer Notifications. The contract it must satisfy:

- It SHALL be subscribed to the Play Console's RTDN Pub/Sub topic for the `com.kornerz.kornerz` application.
- On an RTDN message for `SUBSCRIPTION_PURCHASED`, `SUBSCRIPTION_RENEWED`, `SUBSCRIPTION_CANCELED`, `SUBSCRIPTION_EXPIRED`, `SUBSCRIPTION_REVOKED`, `SUBSCRIPTION_ON_HOLD`, or `SUBSCRIPTION_PAUSED`, it SHALL resolve the user's `Users/{email}` document from the purchase's `obfuscatedExternalAccountId` (or the equivalent linkage) and update the three subscription fields accordingly.
- On `SUBSCRIPTION_REVOKED` or `SUBSCRIPTION_EXPIRED`, it SHALL delete the three fields via `FieldValue.delete()`, matching `SubscriptionFirestoreApi.clearSubscription`.
- It SHALL always set `subscriptionPlatform` to `"Android"` on a Play-driven write.

Because this contract is implemented server-side, the Android client depends on it only through `verifyPurchase`. The client SHALL NOT assume RTDN has been delivered before evaluating the gate.

#### Scenario: RTDN revocation reaches the client on next launch
- **WHEN** an RTDN `SUBSCRIPTION_REVOKED` clears the fields server-side and the user next launches the app
- **THEN** `reconcileEntitlement()` reports `isActive == false` and the client clears its cached entitlement

#### Scenario: The gate does not wait on RTDN
- **WHEN** RTDN delivery is delayed and the local `subscriptionExpiryDate` has passed
- **THEN** `isUserPremiumNow` is already `false` because the gate compares against the current time, independent of any RTDN
