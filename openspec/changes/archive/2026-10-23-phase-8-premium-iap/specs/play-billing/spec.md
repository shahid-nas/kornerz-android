# play-billing Specification

## Purpose
TBD - created by archiving change phase-8-premium-iap. Update Purpose after archive.

## ADDED Requirements

### Requirement: The billing gateway seam and the `dev` flavor's simulated implementation

`feature/subscription` SHALL expose a Play-free interface, `SubscriptionBillingGateway`, that every consumer of billing depends on: `SubscriptionRepository`, `SubscriptionViewModel`, the plans screen's `@EntryPoint`, and `feature/shell`'s `PlayEntitlementChecker` adapter. The interface's surface SHALL be exactly `connectionState`, `purchaseOutcomes`, `attachActivity`, `detachActivity`, `launchPurchase`, `queryProductDetails`, `queryPurchases`, `acknowledgePurchase`, `restore`, `endConnection`.

`launchPurchase` SHALL return `Result<Unit>` on the interface, **not** Play's `BillingResult`. A simulated implementation must be able to honour the contract without importing `com.android.billingclient`. `BillingClientApi` SHALL remain the only file in the app that imports a Play type, and it SHALL translate the Play response code into a typed `SubscriptionException` internally rather than returning the raw code.

`feature/subscription` SHALL declare an `env` flavor dimension with `dev` and `prod` flavors, matching `app`'s, so AGP variant matching selects the right implementation:

| Variant | Binding | Source set |
|---|---|---|
| `dev` | `DevBillingGateway` | `feature/subscription/src/dev/kotlin` |
| `prod` | `BillingClientApi` | `feature/subscription/src/main/kotlin` |

The simulated implementation SHALL NOT be compiled into a `prod` variant. No runtime flag, manifest marker, or remote-config value SHALL be able to activate it in a shipped build.

`DevBillingGateway` SHALL return the two pinned iOS product ids with the pricing iOS declares in `Kornerz-SKConfig.storekit` (monthly `10.99`, yearly `95.9`, per-month equivalent `7.99/month`), SHALL report `BillingConnectionState.Ready` with a synthetic license code (there is no license check to fail), SHALL refuse an unrecognized product id with `SubscriptionError.ProductUnavailable`, and SHALL emit `BillingPurchaseResult.Purchased` **asynchronously** so the ViewModel's acknowledge-then-grant sequence is exercised. It SHALL NOT simulate pending purchases or purchase failures; those paths are covered by `SubscriptionViewModelTest` against a mocked gateway.

`DevBillingGateway` SHALL be unit-tested in `src/testDev/kotlin`, a source set that compiles only into the `dev` variant, so the simulated path cannot silently regress.

The value written to `Users/{email}.subscriptionPlatform` SHALL be flavor-scoped: `"Android"` on `prod`, `"AndroidDev"` on `dev`. The `dev` value exists so simulated entitlements are identifiable in the shared `kornerz-72fba` backend, where premium is derived from `subscriptionExpiryDate` on both platforms and a simulated subscription would otherwise be indistinguishable from a real one. It SHALL be written and never branched on, exactly as `"Android"` already was.

#### Scenario: The dev flavor renders a working paywall with no Play listing
- **WHEN** the `dev` flavor opens the plans screen and `com.kornerz.kornerz.dev` is not a registered Play listing
- **THEN** both plans resolve, the screen renders its full iOS chrome, and no billing-unavailable notice is shown

#### Scenario: A dev purchase grants an entitlement that is identifiable as simulated
- **WHEN** a subscription completes on the `dev` flavor
- **THEN** `Users/{email}` is written with `subscriptionPlatform = "AndroidDev"`, a future `subscriptionExpiryDate`, and the selected `subscriptionType`

#### Scenario: The simulated gateway cannot reach a release build
- **WHEN** a `prod` variant is compiled
- **THEN** `DevBillingGateway` and `DevBillingModule` do not exist, and the Hilt graph binds `BillingClientApi` as the `SubscriptionBillingGateway`

#### Scenario: The purchase outcome is not synchronous
- **WHEN** `launchPurchase` is called on the `dev` flavor
- **THEN** it returns success immediately and `purchaseOutcomes` emits `Purchased` only after the in-flight button state has been observable

### Requirement: BillingClientApi is the only Play Billing boundary
The `feature/subscription` module SHALL contain exactly one type that imports `com.android.billingclient.*`: `BillingClientApi` (`@Singleton`), located at `feature/subscription/data/billing/BillingClientApi.kt`. No ViewModel, composable, `core/model` type, or `core/ui` component SHALL reference a Play type. This mirrors the repository's existing wrapper convention (`feature/community/data/network/*FirestoreApi`, `feature/profile/data/network/UserFirestoreApi`), where SDK types never escape the wrapper.

`BillingClientApi` SHALL be constructor-injected (pure `@Singleton` + `@Inject constructor(context)`), matching the dominant DI pattern; no `@Binds` module SHALL be introduced for it.

Its API surface SHALL consist of exactly:

| Member | Signature | Purpose |
|---|---|---|
| `connectionState` | `StateFlow<BillingConnectionState>` | Client readiness + typed disconnect reason |
| `queryProductDetails` | `suspend fun (ids: List<String>): Result<List<SubscriptionProduct>>` | Resolve the catalog |
| `queryPurchases` | `suspend fun (): Result<List<PurchaseRecord>>` | Read active entitlements |
| `launchPurchase` | `fun (productId: String, offerToken: String?): BillingResult` | Open the Play purchase sheet |
| `acknowledgePurchase` | `suspend fun (token: String): Result<Unit>` | Acknowledge within the 3-day window |
| `restore` | `suspend fun (): Result<List<PurchaseRecord>>` | `queryPurchasesAsync(SUBS)` re-read |
| `purchaseOutcomes` | `Channel`-backed `Flow<BillingPurchaseResult>` | Async purchase results from `PurchasesUpdatedListener` |
| `endConnection` | `fun ()` | Release the client |

The implementation SHALL use `suspend` + `kotlinx.coroutines.tasks.await` for every `Task`-returning Play API, exactly as the existing `*FirestoreApi` wrappers do. It SHALL NOT introduce `withContext(Dispatchers.IO)` — `core/network` does not use it, and Play's own client already dispatches its IPC off the main thread.

#### Scenario: No Play type escapes the wrapper
- **WHEN** `feature/subscription` is compiled and its public API is inspected
- **THEN** no type outside `data/billing/BillingClientApi.kt` references `com.android.billingclient`

#### Scenario: Wrapper is constructor-injected
- **WHEN** the Hilt graph is built
- **THEN** `BillingClientApi` resolves as a `@Singleton` via constructor injection with no `@Provides` or `@Binds` entry

### Requirement: BillingClient lifecycle and connection state
`BillingClientApi` SHALL build a single `BillingClient` with `enablePendingPurchases()` enabled (required by Play Billing v6+ for every subscription, and a hard requirement for v7), register a `BillingClientStateListener`, and translate every listener callback into a `BillingConnectionState` value emitted on `connectionState`.

`BillingConnectionState` SHALL be a sealed type with at least these cases:

| Case | Meaning |
|---|---|
| `Disconnected` | The client is not connected yet, or was closed |
| `Connecting` | `startConnection` / `enablePendingPurchases` in flight |
| `Ready(licenseResponseCode: Int)` | Connected; the Play license check result is carried, not gated on |
| `Unavailable(reason: BillingUnavailableReason)` | Connected attempt failed, or the application id is not a playable Play listing |

`BillingUnavailableReason` SHALL distinguish at minimum: `NoPlayStore` (device has no Play Store / Play Services), `Disconnected(errorCode)`, `UnsupportedApplicationId` (the license check failed because the application id is not registered in Play Console — the `dev` flavor's `com.kornerz.kornerz.dev` case), and `Unknown`.

The license-check response code SHALL be carried on `Ready` as data and SHALL NOT be used to block the flow, because the project's release build is signed with the debug keystore (`app/build.gradle.kts` sets `signingConfig = signingConfigs.getByName("debug")` for both build types), making the fingerprint environment-dependent and untestable.

`endConnection()` SHALL be callable and SHALL be idempotent.

#### Scenario: Successful connection
- **WHEN** `startConnection` completes and no error is reported
- **THEN** `connectionState` emits `Ready(licenseResponseCode)` with the actual license-check code

#### Scenario: Dev flavor is not a playable Play listing
- **WHEN** the app runs from the `dev` flavor whose application id is `com.kornerz.kornerz.dev` and Play rejects the license check
- **THEN** `connectionState` emits `Unavailable(UnsupportedApplicationId)` rather than `Ready`

#### Scenario: Device without Play Store
- **WHEN** the app runs on a device with no Google Play Store
- **THEN** `connectionState` emits `Unavailable(NoPlayStore)` and no purchase UI throws

#### Scenario: End connection is idempotent
- **WHEN** `endConnection()` is called twice
- **THEN** the second call is a no-op and does not throw

### Requirement: Two-product subscription catalog mirroring iOS InAppProduct
The catalog SHALL consist of exactly two products whose ids mirror iOS `InAppManager.InAppProduct` (`kornerz/Kornerz/InAppPurchase/InAppManager.swift:16-19`) byte-for-byte:

| `SubscriptionProductType` | `rawValue` (Play product id / iOS `InAppProduct`) | iOS display name |
|---|---|---|
| `MONTHLY` | `video_call_monthly` | `Monthly` |
| `YEARLY` | `video_call_yearly_sub` | `Yearly` |

`SubscriptionProductType` SHALL live in `core/model` and SHALL be the single source of the product-id strings. The `rawValue` SHALL be what is written to `Users.subscriptionType`, so a user who subscribed on iOS reads as premium on Android and vice versa.

`queryProductDetails(ids)` SHALL project each Play `ProductDetails` into a Compose-free `SubscriptionProduct` (defined in `core/model`) carrying: `planId`, `title`, `priceLabel` (Play's localized `formattedPrice`), `periodLabel`, `pricePerMonthEquivalent` (nullable; non-null only for `YEARLY`), `hasFreeTrialOffer: Boolean`, and `offerTag: String?`.

`pricePerMonthEquivalent` mirrors iOS `SubscriptionViewModel.mothlyPriceForYearlySubscription()` (`:81-91`), which divides the yearly price by 12 and appends `"/month"`.

Trial availability SHALL be read from Play's `freeTrialOfferDetails` / `offerDetails` per product rather than hard-coded, so whichever plan the Play Console configures with a trial drives the UI. This mirrors iOS reading `product.subscription?.introductoryOffer != nil` (`SubscriptionViewModel.swift:73-79`).

#### Scenario: Catalog resolves both plans
- **WHEN** `queryProductDetails(listOf("video_call_monthly", "video_call_yearly_sub"))` completes against a configured Play Console
- **THEN** it returns exactly two `SubscriptionProduct`s, one with `planId == MONTHLY.rawValue` and one with `planId == YEARLY.rawValue`

#### Scenario: Yearly plan carries a per-month equivalent
- **WHEN** the `YEARLY` product resolves
- **THEN** its `pricePerMonthEquivalent` is non-null and equals the yearly price divided by 12 plus the localized `"/month"` suffix

#### Scenario: Monthly plan has no per-month equivalent line
- **WHEN** the `MONTHLY` product resolves
- **THEN** its `pricePerMonthEquivalent` is `null`

#### Scenario: Trial availability comes from Play, not from code
- **WHEN** the Play Console defines a free-trial offer on only the monthly base plan
- **THEN** the monthly `SubscriptionProduct.hasFreeTrialOffer` is `true` and the yearly one is `false`, with no code change required

#### Scenario: Unregistered application id yields an empty catalog, not a throw
- **WHEN** `queryProductDetails` runs on the `dev` flavor
- **THEN** it returns `Result.failure` with `SubscriptionError.BillingUnavailable` (or an empty list), never an uncaught exception

### Requirement: SubscriptionError taxonomy
`feature/subscription/data/SubscriptionError.kt` SHALL define a `sealed interface SubscriptionError` and a `class SubscriptionException(val errorKind: SubscriptionError, message: String) : RuntimeException(message)`. This mirrors the existing `AuthError` + `AuthException` pattern (`core/network/.../auth/AuthResult.kt:19-34` and `AuthRepository.kt:203-206`); no general `AppError` type exists in the codebase (verified: 0 hits), so P8 introduces the feature-scoped equivalent rather than inventing a global one.

`SubscriptionError` SHALL have at least these cases and their mappings from `BillingResultCode`:

| Case | Play source |
|---|---|
| `BillingUnavailable` | `SERVICE_UNAVAILABLE`, `SERVICE_DISCONNECTED`, `BILLING_UNAVAILABLE`, `FEATURE_NOT_SUPPORTED` |
| `ProductUnavailable` | `ITEM_UNAVAILABLE`, `PRODUCT_NOT_AVAILABLE` |
| `UserCancelled` | `USER_CANCELED` |
| `NetworkError` | `NETWORK_ERROR`, `IO_ERROR`, `SERVICE_TIMEOUT` |
| `PurchasePending` | `ITEM_ALREADY_OWNED` is distinct; `PurchasePending` covers the deferred/deferred-upgrade flow surfaced by `PurchasesUpdatedListener` with `PENDING` purchase state |
| `NotAcknowledged` | acknowledgement failure — surfaces after a successful purchase |
| `Unknown` | any unmapped code, or a client exception |

`UserCancelled` SHALL NOT be surfaced as an error state on the plans screen; it is a normal return path and the screen SHALL simply re-enable its subscribe button.

#### Scenario: User cancels the Play sheet
- **WHEN** `launchBillingFlow` returns `USER_CANCELED`
- **THEN** `BillingPurchaseResult.Failed(SubscriptionError.UserCancelled)` is emitted and the plans screen clears `isPurchasing` without showing an error message

#### Scenario: Service unavailable
- **WHEN** `queryProductDetails` returns `BillingResultCode.SERVICE_UNAVAILABLE`
- **THEN** it returns `Result.failure(SubscriptionException(SubscriptionError.BillingUnavailable, ...))`

#### Scenario: Acknowledgement failure is a distinct error
- **WHEN** a purchase succeeds but `acknowledgePurchase` fails
- **THEN** the outcome is `SubscriptionError.NotAcknowledged`, which is distinct from `NetworkError` and blocks the success navigation

### Requirement: Every purchase is acknowledged before entitlement is granted
`SubscriptionViewModel` SHALL call `BillingClientApi.acknowledgePurchase(token)` for every `Purchase` whose `purchaseState == PURCHASED` and `isAcknowledged == false`, and SHALL NOT proceed to `SubscriptionNavEvent.PurchaseSucceeded` (and therefore SHALL NOT write the entitlement) until `acknowledgePurchase` returns success.

This is deliberately stricter than iOS, which has no acknowledgement concept because that is StoreKit's responsibility. It is required because Google refunds any purchase left unacknowledged after three days, which would silently reverse an entitlement the user was already shown as active.

#### Scenario: Successful unacknowledged purchase
- **WHEN** a `Purchase` arrives with `purchaseState == PURCHASED` and `isAcknowledged == false`
- **THEN** `acknowledgePurchase(token)` is called, and only on its success is the entitlement written and `PurchaseSucceeded` emitted

#### Scenario: Already-acknowledged purchase
- **WHEN** a `Purchase` arrives with `isAcknowledged == true`
- **THEN** `acknowledgePurchase` is not called again and the entitlement proceeds immediately

#### Scenario: Acknowledgement failure blocks the grant
- **WHEN** `acknowledgePurchase` returns `Result.failure`
- **THEN** no entitlement write occurs and `PurchaseFailed(SubscriptionError.NotAcknowledged)` is emitted

### Requirement: Restore re-reads active subscriptions from Play
`BillingClientApi.restore()` SHALL invoke `queryPurchasesAsync(QueryPurchasesParams(SUBS))` — the subscriptions-only query type — and return every non-consumed purchase mapped to a `PurchaseRecord`. This is the Android equivalent of iOS's `InAppManager.restore(completion:)` (`:103-109`), which calls `AppStore.sync()` and re-reads `currentEntitlements`.

A restore SHALL NOT create a new purchase and SHALL NOT charge the user; it only re-derives the entitlement. When a restore finds an active purchase for the current user that the local Firestore record does not reflect, the entitlement write SHALL proceed exactly as it does for a fresh purchase (including `subscriptionPlatform = "Android"`), so a user who subscribed on iOS and installed Android can restore their premium state.

Purchases whose `purchaseState != PURCHASED` (for example `PENDING`) SHALL be excluded from the restored set.

#### Scenario: Restore finds an existing purchase
- **WHEN** the device holds an active, non-consumed subscription purchase and the user taps "Restore Purchase"
- **THEN** `restore()` returns it and the entitlement is written to Firestore with `subscriptionPlatform = "Android"`

#### Scenario: Restore with no purchases
- **WHEN** the device holds no subscription purchases and the user taps "Restore Purchase"
- **THEN** `restore()` returns an empty list and the plans screen shows a "no active subscription" message rather than an error

#### Scenario: Pending purchases are excluded from restore
- **WHEN** the device holds a purchase in the `PENDING` state
- **THEN** `restore()` does not include it in the returned list

### Requirement: Purchase outcomes are delivered asynchronously
`launchBillingFlow` SHALL return `BillingResult` synchronously (mirroring Play's non-`Task` API), and the actual purchase outcome SHALL arrive on `purchaseOutcomes`, backed by a `Channel<BillingPurchaseResult>` drained by the ViewModel. This is Play's direct analogue of iOS's `InAppManager.buyProduct(product:)` (`InAppManager.swift:63-89`) awaiting `product.purchase()` plus the `listenForTransactions()` background listener (`:111-129`).

`BillingPurchaseResult` SHALL be a sealed type with `Purchased(token: String, productId: String, purchaseTimeMillis: Long)`, `Pending`, and `Failed(error: SubscriptionError)`. The `Purchased` payload SHALL NOT expose a Play `Purchase` to the ViewModel.

The `PurchasesUpdatedListener` SHALL be registered once, for the lifetime of the `BillingClient`, and SHALL remain active after the plans screen is dismissed so a purchase completed outside the app is still observed on the next resume.

#### Scenario: Purchase completes asynchronously
- **WHEN** `launchBillingFlow` returns `OK` and the `PurchasesUpdatedListener` later fires with a `PURCHASED` purchase
- **THEN** `purchaseOutcomes` emits `BillingPurchaseResult.Purchased` with only the plain-data payload

#### Scenario: Listener outlives the plans screen
- **WHEN** the user navigates away from `subscription/plans` and a `PurchasesUpdatedListener` callback fires
- **THEN** the result is still delivered on `purchaseOutcomes`

## References

- `kornerz/Kornerz/InAppPurchase/InAppManager.swift` — iOS source-of-truth for the product catalog (`:16-19`), purchase (`:63-89`), verification (`:91-101`), transaction listener (`:111-129`), entitlement derivation (`:131-146`), and restore (`:103-109`)
- `kornerz/Kornerz/InAppPurchase/Kornerz-SKConfig.storekit` — iOS subscription group + product ids + 1-week introductory offer (`:80`, `:185-200`, `:231-233`)
- `docs/phases.md:259` — "Google Play Billing v6+ integration"
- `docs/architecture.md:230` — the canonical library list entry for Google Play Billing v6+
- `docs/project-brief.md:223` — "Google Play Billing v6+; RTDN to Cloud Function updates `subscriptionPlatform = "Android"`"