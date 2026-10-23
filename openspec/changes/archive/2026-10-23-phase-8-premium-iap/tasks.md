## 1. Module scaffolding + dependencies

- [x] 1.1 Add `billing = "7.0.0"` to `[versions]` and `android-billing = { group = "com.android.billingclient", name = "billing-ktx", version.ref = "billing" }` to `[libraries]` in `gradle/libs.versions.toml` — see `android-build` spec
- [x] 1.2 Create `feature/subscription/` module with `build.gradle.kts` mirroring `feature/profile/build.gradle.kts` (namespace `com.kornerz.feature.subscription`, compileSdk 35, minSdk 26, JVM 17, Compose, Hilt, kapt `correctErrorTypes = true`, `implementation(libs.android.billing)`, test stack) — see `android-build` spec §"feature/subscription module is registered"
- [x] 1.3 Wire `:feature:subscription` into `settings.gradle.kts` and add `implementation(project(":feature:subscription"))` to `app/build.gradle.kts`. Confirm no other `feature/*` module depends on it (arch §2.5)
- [x] 1.4 Create the package structure `feature/subscription/{plans,plans/components,success,navigation,data,data/billing,data/network}`
- [x] 1.5 Confirm **no** per-module `repositories {}` block is added — `settings.gradle.kts` uses `RepositoriesMode.FAIL_ON_PROJECT_REPOS`
- [x] 1.6 Verify the billing AAR contributes `com.android.vending.BILLING` to the merged manifest and **do not** add an explicit `<uses-permission>` to `app/src/main/AndroidManifest.xml`
- [x] 1.7 Run `./gradlew :feature:subscription:assembleDevDebug` on the empty scaffold — should succeed before any code lands

## 2. Domain model

- [x] 2.1 Add `SubscriptionProductType` enum to `core/model/SubscriptionProductType.kt` with exactly two values — `MONTHLY("video_call_monthly")` and `YEARLY("video_call_yearly_sub")` — mirroring iOS `InAppManager.InAppProduct` (`InAppManager.swift:16-19`). Compose-free; `rawValue` is what gets written to `Users.subscriptionType` — see `play-billing` spec
- [x] 2.2 Add `SubscriptionProduct` and `SubscriptionPlan` `@Serializable` data classes to `core/model/` — Compose-free projections carrying `planId`, `title`, `priceLabel`, `periodLabel`, `pricePerMonthEquivalent` (nullable, non-null only for `YEARLY`), `hasFreeTrialOffer`, `offerTag` — see `play-billing` spec §"Two-product subscription catalog"
- [x] 2.3 Add `User.subscriptionExpiryInstant: Instant?` derived property to `core/model/User.kt` (parses the ISO-8601 `subscriptionExpiryDate` via `kotlinx-datetime`, returns `null` on malformed input rather than throwing) — see `premium-entitlement` spec
- [x] 2.4 Add `User.isUserPremiumNow: Boolean` derived property (`subscriptionExpiryInstant?.isAfter(Clock.System.now()) == true`), mirroring iOS `AppHelper.isUserPremium` (`AppHelper.swift:39-45`)
- [x] 2.5 Correct the KDoc on `User.isPremium` — state that it is a shared-document compatibility field written at signup by `FirestoreAuthApi.signUp` and **not** the gate input; `isUserPremiumNow` is
- [x] 2.6 Add `TRIAL_PERIOD_DAYS = 7` to `core/model` as documentation parity with iOS `Constants.trialPeriodInSeconds` (`Config.swift:117`). Document that it has zero readers on both platforms and is never branched on — the trial comes from Play's free-trial offer

## 3. Storage — session user patch

- [x] 3.1 Add `suspend fun updateUser(transform: (User) -> User): Result<Unit>` to `core/storage/.../session/SessionRepository.kt` — read the current `SessionRecord`, apply `transform` to `user`, write back with `token`/`expiresAt` preserved verbatim inside one `dataStore.updateData` block. Return `Result.failure` on error; never throw — see `session-storage` spec
- [x] 3.2 Confirm the anonymous-detection invariants in `SessionRepository.toState()` are untouched by the new method, and that no transient `Anonymous` emission occurs on a successful patch
- [x] 3.3 Remove the dead `private val scope` in `SessionRepository` if it is still unused (it is dead today per the audit) — opportunistic cleanup, skip if it breaks anything

## 4. Data layer — Play Billing wrapper

- [x] 4.1 Create `BillingClientApi` (`@Singleton`, `@Inject constructor(context: Context)`) at `feature/subscription/data/billing/BillingClientApi.kt` as the **only** file importing `com.android.billingclient.*` — see `play-billing` spec §"BillingClientApi is the only Play Billing boundary"
- [x] 4.2 Build the client with `enablePendingPurchases()`, register the `BillingClientStateListener`, and translate every callback into `connectionState: StateFlow<BillingConnectionState>`
- [x] 4.3 Add `BillingConnectionState` (`Disconnected` / `Connecting` / `Ready(licenseResponseCode)` / `Unavailable(reason)`) and `BillingUnavailableReason` (`NoPlayStore` / `Disconnected(errorCode)` / `UnsupportedApplicationId` / `Unknown`). Carry the license-check code as data; do **not** gate on it (release is signed with the debug keystore)
- [x] 4.4 Implement `suspend fun queryProductDetails(ids: List<String>): Result<List<SubscriptionProduct>>` using `QueryProductDetailsParams` + `kotlinx.coroutines.tasks.await`; project each `ProductDetails` into `SubscriptionProduct` with Play's localized `formattedPrice`. **No `withContext(Dispatchers.IO)`** — match `core/network`
- [x] 4.5 Compute `pricePerMonthEquivalent` for `YEARLY` (price ÷ 12 + localized `"/month"`), mirroring iOS `SubscriptionViewModel.mothlyPriceForYearlySubscription()` (`:81-91`); leave it `null` for `MONTHLY`
- [x] 4.6 Read trial availability per product from Play's `freeTrialOfferDetails` / `offerDetails` into `hasFreeTrialOffer` + `offerTag` — do not hard-code
- [x] 4.7 Implement `suspend fun queryPurchases(): Result<List<PurchaseRecord>>` and `suspend fun restore(): Result<List<PurchaseRecord>>` via `queryPurchasesAsync(QueryPurchasesParams(SUBS))`; exclude non-`PURCHASED` states; restore is re-derivation only, never a new charge
- [x] 4.8 Implement `fun launchPurchase(productId: String, offerToken: String?): BillingResult` (non-suspend — Play's API is not a `Task`) and register `PurchasesUpdatedListener` for the client's lifetime so results arrive even when the plans screen is off-screen
- [x] 4.9 Add `purchaseOutcomes: Flow<BillingPurchaseResult>` backed by a `Channel`, where `BillingPurchaseResult` is `Purchased(token, productId, purchaseTimeMillis)` / `Pending` / `Failed(error)` — the `Purchased` payload must carry no Play type
- [x] 4.10 Implement `suspend fun acknowledgePurchase(token: String): Result<Unit>` via `AcknowledgePurchaseParams`
- [x] 4.11 Implement `fun endConnection()` — idempotent
- [x] 4.12 Map every `BillingResultCode` to `SubscriptionError` per the `play-billing` spec table; confirm `USER_CANCELED` maps to `UserCancelled` and that no `com.android.billingclient` type leaks past the wrapper

## 5. Data layer — error type

- [x] 5.1 Create `feature/subscription/data/SubscriptionError.kt` with `sealed interface SubscriptionError` (`BillingUnavailable`, `ProductUnavailable`, `UserCancelled`, `NetworkError`, `PurchasePending`, `NotAcknowledged`, `Unknown`) and `class SubscriptionException(val errorKind: SubscriptionError, message: String) : RuntimeException(message)` — mirrors `AuthError`/`AuthException` (`AuthResult.kt:19-34`, `AuthRepository.kt:203-206`). Do **not** introduce a global `AppError` type

## 6. Data layer — Firestore + Cloud Functions

- [x] 6.1 Create `SubscriptionFirestoreApi` (`@Singleton`) in `feature/subscription/data/network/` with `suspend fun setSubscription(email, expiryDate: Instant, planId, platform): Result<Unit>` — a Firestore `updateData` on `Users/{email}` writing exactly three keys (`subscriptionExpiryDate` as `Timestamp`, `subscriptionType`, `subscriptionPlatform`) — see `premium-entitlement` spec
- [x] 6.2 Implement the identical-expiry no-op guard in `setSubscription`, mirroring iOS `UserRepository.swift:168`
- [x] 6.3 Confirm the write targets `Users/{email}` and **not** `codeUser` as the key and **not** the lowercase `users/{uid}` FCM path
- [x] 6.4 Implement `suspend fun clearSubscription(email): Result<Unit>` deleting the same three keys via `FieldValue.delete()`, mirroring iOS `setUserFree()` (`:149-163`)
- [x] 6.5 Add `SubscriptionVerificationResult` `@Serializable` DTO (`expiryDate: String?`, `isActive: Boolean`, `autoRenewing: Boolean?`, `acknowledged: Boolean?`) in `feature/subscription/data/network/`
- [x] 6.6 Create `SubscriptionCloudFunctionsApi` (`@Singleton`) in `feature/subscription/data/network/` with `suspend fun verifyPurchase(token, productId): Result<SubscriptionVerificationResult>` calling the `verifySubscription` HTTPS-callable — mirror `feature/profile/.../data/network/CloudFunctionsApi.kt` line-for-line. `FirebaseFunctions` is already provided by `core/network/.../di/FirebaseModule.kt:39-41`; add **no** Hilt provider
- [x] 6.7 Author **no** Cloud Function source — the repository has no `functions/` dir, no `*.ts`, no `firebase.json`. The `subscription-verification` spec holds the contract for whoever implements it

## 7. Data layer — repository

- [x] 7.1 Create `SubscriptionRepository` (`@Singleton`) in `feature/subscription/data/` with `observeCatalog`, `grantEntitlement`, `revokeEntitlement`, `refreshCachedUser`, `purchase`, `restore` — see `premium-entitlement` spec
- [x] 7.2 Resolve the current email from `SessionRepository.data` (`SessionState.Authenticated`), matching `GamificationRepository.getUserPoints()`; return `Result.failure` when anonymous
- [x] 7.3 Implement `grantEntitlement` → `setSubscription(platform = "Android")` → `refreshCachedUser`
- [x] 7.4 Implement `refreshCachedUser` as read-Firestore-then-`SessionRepository.updateUser` so the cached user never diverges for longer than one purchase flow
- [x] 7.5 Implement `reconcileEntitlement()`: skip entirely when not premium and no local Play purchase; otherwise call `verifyPurchase` and on `Result.success` with `isActive == false` call `clearSubscription`. On `Result.failure`, leave local state untouched — see `subscription-verification` spec
- [x] 7.6 Add a `SubscriptionDataModule` marker object under `@InstallIn(SingletonComponent::class)`, matching the convention in 13 of 15 DI files. **No** `@Binds` — `BillingClientApi` is constructor-injected

## 8. Shared UI components

- [x] 8.1 Add `trailingText: String? = null` to `ProfileMenuRow` in `feature/profile/.../components/ProfileMenuRow.kt` — right-aligned, vertically centered, 16-pt trailing inset. Confirm all six existing call sites compile and render unchanged — see `user-profile` spec
- [x] 8.2 Add `KornerzCapsuleButton` to `core/ui/components/` with an explicit black-on-white / white-on-black style parameter, `text`, `onClick`, `enabled = true`, full-width behavior. Colors from `KornerzColors`, type from `KornerzTypography`; no inline hex — see `subscription-plans` spec
- [x] 8.3 Confirm `KornerzButton` is **not** modified — its 52-dp height, 16-dp shape, and theme-primary fill are used by every P0–P7 screen
- [x] 8.4 Add `SubscriptionPlanCard` to `feature/subscription/plans/components/` taking `product: SubscriptionProduct`, `isSelected: Boolean`, `onClick: () -> Unit` — white background, black foreground, 24-pt corner radius, 2-pt black stroke when selected. Monthly renders one price line; yearly renders the price plus a gray 20-pt per-month-equivalent line
- [x] 8.5 Ship a working drawable for the 320-pt top image band (vector or solid/gradient). Do **not** attempt to reproduce the missing iOS `subscription_bg` bitmap
- [x] 8.6 Add `@Preview` composables for `KornerzCapsuleButton` (both variants), `SubscriptionPlanCard` (selected + unselected, monthly + yearly), and the updated `ProfileMenuRow` with `trailingText`

## 9. Subscription plans screen (11.1 + 11.2)

- [x] 9.1 Create `SubscriptionContract.kt` in `feature/subscription/plans/` with `SubscriptionState(products, selected, isTrialEligible, isPurchasing, isRestoring, billingNotice, error)` and `sealed interface SubscriptionNavEvent` (`PurchaseSucceeded`, `PurchaseFailed`, `Restored`, `OpenPrivacyPolicy`, `OpenTermsOfUse`, `Dismissed`) — see `subscription-plans` spec
- [x] 9.2 Create `SubscriptionViewModel` (`@HiltViewModel`): `navEvents` as `Channel<SubscriptionNavEvent>` → `receiveAsFlow()`; init observes `connectionState` then calls `queryProductDetails` for both ids
- [x] 9.3 Derive `isTrialEligible` from the **selected** product's `hasFreeTrialOffer` on every selection change; default `selected` to `SubscriptionProductType.MONTHLY`
- [x] 9.4 Implement the subscribe tap: set `isPurchasing`, launch the purchase, collect `purchaseOutcomes`, acknowledge **before** granting, then `grantEntitlement` → `PurchaseSucceeded`. `NotAcknowledged` must block the success navigation
- [x] 9.5 Implement the restore tap: set `isRestoring`, call `restore()`, grant entitlement for any recovered purchase, emit `Restored`. `SubscriptionError.UserCancelled` must leave `error` null
- [x] 9.6 Create `SubscriptionScreen` + `SubscriptionContent` reproducing iOS `SubscriptionScreen.swift`: `#68B6CC` full-bleed background behind the status bar, 320-pt image band, "Welcome {nickname}" with the `"Klouder"` fallback, the blurb, the conditional free-trial line, exactly two plan cards, the "1 week free, then {price}" caption, the full-width black-capsule subscribe button ("Try It Free" vs "Subscribe"), "Restore Purchase", and the two bottom links. **No** app bar and **no** discount badge and **no** feature list
- [x] 9.7 Render the four states: `KornerzLoadingView`, `KornerzErrorState` with working retry, the non-fatal `billingNotice` for `BillingConnectionState.Unavailable` (screen chrome still renders, subscribe disabled), and the loaded screen
- [x] 9.8 Add a working close affordance that emits `Dismissed` (iOS's is commented out at `SubscriptionController.swift:22`) — required so a dismissed paywall is not a dead end
- [x] 9.9 Wire the Privacy Policy tap to `OpenPrivacyPolicy` (reuses the P6 `profile/privacy-policy` destination) and the Terms tap to `OpenTermsOfUse` (`https://www.apple.com/legal/internet-services/itunes/dev/stdeula/` via `Intent.ACTION_VIEW`)

## 10. Subscription success screen (11.3)

- [x] 10.1 Create `SubscriptionSuccessViewModel` (`@HiltViewModel`) owning the single `Dismissed` event — added for contract consistency; every P0–P7 screen has a VM
- [x] 10.2 Create `SubscriptionSuccessScreen` + `SubscriptionSuccessContent`: `#68B6CC` background, 120-dp checkmark-seal glyph (`Icons.Filled.Verified`), "Subscription activated successfully" at 26-pt bold, full-width white-capsule "Done". No back affordance, no app bar
- [x] 10.3 Confirm the success screen performs **no** Firestore write — the entitlement write happens upstream in the purchase flow

## 11. Premium gate

- [x] 11.1 Create `PremiumGate` (`@Singleton`) in `feature/shell/` with `shouldPresent: StateFlow<Boolean>`, `suspend fun requestPresent(): Boolean`, `markDismissed()`, `markSatisfied()`, `reset()` — see `premium-gate` spec
- [x] 11.2 Implement the five clauses: not-already-on-plans, authenticated, no active Play entitlement, `isUserPremiumNow == false`, not dismissed this process. Clause 4 MUST use `isUserPremiumNow`, never the raw `isPremium`
- [x] 11.3 Verify clause 5 (the dismissal latch) is cleared by both `markSatisfied()` and `reset()`, and that `reset()` is invoked on logout and on the P6 `userDeletedHisAccount` event bus

## 12. Navigation + shell wiring

- [x] 12.1 Create `SubscriptionRoute` sealed class in `feature/subscription/navigation/` with `Plans` (`subscription/plans`) and `Success` (`subscription/success`) — see `android-app-shell` spec
- [x] 12.2 Register both routes as top-level destinations on the root `NavHost` in `AppShell.kt`, outside every tab's nested `NavHost`
- [x] 12.3 Extend the bottom-bar-hidden `Regex` at `AppShell.kt:111-113` with `subscription/.*`, and ensure both routes render behind the status bar
- [x] 12.4 Wire `ShellViewModel` to collect `PremiumGate.shouldPresent` from its two call sites only (post-login in `feature/auth`, shell resume) and navigate to `SubscriptionRoute.Plans`. Add **no** destination-change or tab-switch evaluation
- [x] 12.5 Wire `SubscriptionNavEvent.PurchaseSucceeded` → navigate forward to `Success`; `Dismissed` → pop back + `markDismissed()`; `SubscriptionSuccessNavEvent.Dismissed` → pop back + `markSatisfied()`. Capture the pre-gate destination on gate open so the unwind is deterministic
- [x] 12.6 Wire `OpenPrivacyPolicy` → `profile/privacy-policy` and `OpenTermsOfUse` → `Intent.ACTION_VIEW`
- [x] 12.7 Reset `PremiumGate` on logout (`ShellViewModel.onLogout()`) and on the `userDeletedHisAccount` bus

## 13. Profile integration

- [x] 13.1 Add `OpenPremium` to `ProfileNavEvent` and `onPremiumTap()` to `ProfileViewModel` in `feature/profile/.../view/ProfileContract.kt` + `ProfileViewModel.kt`
- [x] 13.2 Add the always-visible Premium row to `ProfileContent`'s `LazyColumn`, first in the menu, ahead of Notifications
- [x] 13.3 Compute the row's `trailingText` from the session `User` observed by `ProfileViewModel`: `Premium` (free) / `Monthly` / `Yearly` / `Active until {yyyy-MM-dd}` (expiry fallback when `subscriptionType` is null or unrecognized)
- [x] 13.4 Thread `onNavigateToPremium` through `ProfileScreen` + `ProfileContent` + the existing `@Preview`, and update `AppShell.profileGraph(...)` to route it to `SubscriptionRoute.Plans`

## 14. Read-path fix (blocking)

- [x] 14.1 Map `isUserPremium`, `subscriptionExpiryDate`, `subscriptionType`, and `subscriptionPlatform` in `UserFirestoreApi.toUserOrNull()` (`feature/profile/.../data/network/UserFirestoreApi.kt:156-177`) — today it drops all four, so every Profile read returns a permanently-free user. **Hard prerequisite**: without this the Profile row's trailing label and the gate's entitlement input are both wrong
- [x] 14.2 Confirm the decoded `User.isUserPremiumNow` evaluates correctly for a document written by the iOS app (`subscriptionPlatform = "iOS"`) — pinned by `UserFirestoreApiPremiumMappingTest."a document written by the iOS app decodes as premium"`
- [x] 14.3 Add a round-trip unit test asserting the four fields survive `Users/{email}` → `User` decode — `feature/profile/src/test/.../UserFirestoreApiPremiumMappingTest.kt`

## 15. Testing

- [x] 15.1 `core:model` unit tests: `SubscriptionProductTypeTest` (raw values are exactly `video_call_monthly` / `video_call_yearly_sub`; iOS parity), `SubscriptionProductTest` (`pricePerMonthEquivalent` non-null only for `YEARLY`), `UserPremiumTest` (future / past / null / malformed expiry, plus "raw `isPremium = false` with a future expiry still yields `isUserPremiumNow == true`")
- [x] 15.2 `core:storage` unit test: `SessionRepositoryTest.updateUser` — token and `expiresAt` preserved verbatim, session stays `Authenticated` with no transient `Anonymous`, anonymous record stays anonymous, write failure returns `Result.failure`
- [x] 15.3 `feature:subscription` unit tests with `mockk` at the wrapper boundary (the codebase's only pattern — there is no Firestore fake, no Robolectric, no Hilt test harness): `BillingClientApiTest` (empty catalog on an unregistered app id returns failure, not a throw), `SubscriptionFirestoreApiTest` (three keys written, `updateData` not `setData`, identical-expiry no-op), `SubscriptionRepositoryTest` (`grantEntitlement` writes then refreshes; anonymous session fails cleanly), `SubscriptionCloudFunctionsApiTest` (missing function returns `Result.failure`)
- [x] 15.4 `SubscriptionViewModelTest` with `Dispatchers.setMain(UnconfinedTestDispatcher())`: init resolves the catalog, `isTrialEligible` follows selection, `isPurchasing` clears on every terminal outcome, `UserCancelled` leaves `error` null, ack failure blocks the success event
- [x] 15.5 `PremiumGateTest`: each of the five clauses independently blocks presentation; `markSatisfied` and `reset` clear the latches
- [x] 15.6 `UserFirestoreApiPremiumMappingTest`: the four fields round-trip and an iOS-written document decodes correctly — see §14.3
- [x] 15.7 Compose UI tests: skeletons only — `feature/subscription/src/androidTest/` now holds `SubscriptionContentTest` + `SubscriptionChromeTest` and `SubscriptionSuccessContentTest`; every case is `@Ignore`d and the block is a bare `createComposeRule()` harness. **Not run**: no emulator is provisioned and CI builds `assembleDevDebug` only. Verified to compile and package via `:feature:subscription:assembleDebugAndroidTest`
- [x] 15.8 Integration test against the Firestore emulator: **deferred** (no emulator harness exists; requires standing one up) — confirmed absent: no `useEmulator`, no `FIRESTORE_EMULATOR_HOST`, no emulator Gradle config anywhere in the build

## 16. Polish + exit checklist

- [x] 16.1 Confirm every user-facing string on 11.1–11.3 matches the iOS English values recorded in the `subscription-plans` spec references table — verified one-for-one against `SubscriptionContent.kt` (`Welcome $nickname`, the blurb, `Start with a 1 week free trial.`, `1 week free, then {price}`, `Try It Free` / `Subscribe`, `Restore Purchase`, `Privacy Policy`, `Term of Use`, the `Billing is unavailable in this build` notice), `SubscriptionSuccessContent.kt` (`Subscription activated successfully`, `Done`), and `SubscriptionPlanCard.kt` (Play-localized titles + the grey per-month line)
- [x] 16.2 Confirm all three subscription screens are RTL-safe and preview under the `en-rXA` pseudo-locale (per `phases.md` Principle 7 and `project-brief.md` §11.7 — English only this phase) — layout uses `Alignment.TopEnd` / `TextAlign.End` / `padding(horizontal = …)` with no fixed start offsets, and `locale = "en-rXA"` previews were added to `SubscriptionContent` (2) and `SubscriptionSuccessContent`
- [x] 16.3 Run `./gradlew :app:assembleDevDebug`
- [ ] 16.4 Run `./gradlew :app:installDevDebug` on an API 26 emulator and an API 35 emulator — **partial**: installed and launched clean on the API 35 AVD (`Medium_Phone_API_35`). **API 26 is not provisioned** — the local SDK has only an `system-images/android-35` image, so there is no API 26 AVD to install onto without first downloading it
- [x] 16.5 Launch and confirm no crash via `adb logcat`; confirm the `dev` flavor shows the non-fatal billing-unavailable notice rather than crashing — **launch is clean, and this task caught two real launch crashes**, both the same P8 wiring bug and both fixed:
  - `PremiumGateEntryPoint` (`app/.../SubscriptionRoutes.kt`) was `@InstallIn(ActivityComponent::class)` but resolved with `EntryPointAccessors.fromApplication`, which only sees `SingletonComponent` entry points → `ClassCastException` at `AppRoot.kt:58`, killing the app on every launch. Now `SingletonComponent`.
  - `BillingClientApiEntryPoint` (`feature/subscription/.../SubscriptionScreen.kt`) had the identical mismatch → `ClassCastException` the moment the plans screen composed. Now `SingletonComponent`.
  - After both fixes: `pm clear` + `am start` leaves the process alive with **no `FATAL EXCEPTION` in logcat**. The billing-unavailable *notice* itself is no longer the `dev` outcome — §18 replaced it with `DevBillingGateway`, which reports `Ready`, so the `dev` build now renders the working paywall instead. The notice path still needs a real `prod` build on an unregistered id to be seen.
- [x] 16.6 Run `./gradlew :app:lintDevDebug` — BUILD SUCCESSFUL, no errors; report at `app/build/reports/lint-results-devDebug.html`
- [x] 16.7 Run the full unit-test suite (`:core:model:test`, `:core:storage:test`, `:feature:subscription:test`, `:feature:profile:test`, `:feature:shell:test`) — `./gradlew test` green: **242 tests, 0 failures** (core:model 50, core:storage 14, feature:community 2, feature:nooks 4, feature:notifications 6, feature:profile 18, feature:shell 28, feature:subscription 120)
- [ ] 16.8 Capture one screenshot per in-scope screen (11.1 + 11.2 plans, 11.3 success, the Profile Premium row) and attach to the PR — **manual**: needs a signed-in device session and an attached emulator capture
- [ ] 16.9 Record a ≤5-min Loom walkthrough: open the paywall via the Profile row, subscribe on a `prod` build, see premium unlock, restore, see it expire — **manual and blocked on Play**: `com.kornerz.kornerz` is not registered in Play Console (`docs/phases.md:411` open question 6), so no real purchase can complete
- [ ] 16.10 Create the `phase-8-end` git tag and push — **manual**: a tag and a push are not performed without an explicit request
- [ ] 16.11 Share the demo-ready APK with PM / Finance / QA for review — **manual**: distribution outside the repository

## 17. Deferred (documented, not implemented)

- [x] 17.1 **No** Cloud Function source. The repository has no `functions/` dir, no `*.ts`, no `firebase.json`. `SubscriptionCloudFunctionsApi` + `SubscriptionVerificationResult` are the whole deliverable, matching the P6 `deleteUserAccount` precedent — confirmed: zero `*.ts` / `firebase.json` / `functions/` hits outside build output
- [x] 17.2 **No** `manageSubscriptions` hand-off to the Play Console (P10 polish). The P8 demo exercises cancellation via the trial/expiry path, not a client-side cancel button — confirmed: no `manageSubscriptions`, `BillingClientParams`, or Play-Console intent anywhere
- [x] 17.3 **No** deep-link or push entry into the paywall — iOS `DeeplinkManager.DeepLink` (`:13-54`) has no subscription case; P10 owns deep links. Confirmed: `SubscriptionRoute` is referenced only from `AppShell` / `AppRoot` / `SubscriptionRoutes` / `ProfileContract`, never from a deep-link or FCM handler
- [x] 17.4 **No** pt-BR localization of the new screens (follow-up per `project-brief.md` §11.7) — confirmed: no `values-*` resource directories exist anywhere in the build
- [x] 17.5 **No** Play Console registration of the `com.kornerz.kornerz.dev` application id — an unresolved `docs/phases.md:411` open question. **Superseded by §18**: the graceful-disconnect path is still what a *real* Play failure maps to, but `dev` now binds `DevBillingGateway` so the paywall is demonstrable with no console work at all. `app/build.gradle.kts` still declares `applicationIdSuffix = ".dev"` and no console work happened in-repo
- [x] 17.6 **No** release-signing change — `docs/phases.md:407` open question 3. Both build types still use the debug keystore with `isMinifyEnabled = false`, so no release build can be uploaded to Play as-is. The P8 exit checklist is met with a signed debug build — confirmed: `app/build.gradle.kts:41,45,51` unchanged
- [x] 17.7 **No** premium *feature* gating beyond the paywall. The features premium unlocks (unlimited Nook video calls, per `subscription_feature_msg`) land in P9/P10; P8 ships the gate and the entitlement — confirmed: `isUserPremiumNow` is read only by `PremiumGate` (clause 4), `SubscriptionRepository.reconcileEntitlement`, and `ProfileViewModel`'s row label
- [x] 17.8 **No** purchase analytics — no analytics service is wired anywhere in the codebase — confirmed: no `firebase-analytics`, `FirebaseAnalytics`, or `logEvent` anywhere in sources, build files, or the version catalog
- [x] 17.9 **No** reproduction of the dead iOS artifacts: the missing `subscription_bg` bitmap and the unbound `subscription_features.json` unlock list — confirmed: `subscription_bg` appears only in KDoc comments, the band is Android's own `bg_subscription_band.xml`, and no `subscription_features.json` / `featureFile` exists

## 18. Dev-flavor simulated billing (follow-up: Play has no development bypass)

- [x] 18.1 Add an `env` (`dev`/`prod`) flavor dimension to `feature/subscription` so AGP variant matching selects `feature:subscription-dev` for `app:devDebug`
- [x] 18.2 Add `SubscriptionBillingGateway` — a Play-free interface carrying `connectionState`, `purchaseOutcomes`, `attachActivity`/`detachActivity`, `launchPurchase`, `queryProductDetails`, `queryPurchases`, `acknowledgePurchase`, `restore`, `endConnection`. `launchPurchase` returns `Result<Unit>`, not Play's `BillingResult`, so a simulated implementation never imports `com.android.billingclient`
- [x] 18.3 Make `BillingClientApi` implement the interface; translate the Play response code to a typed `SubscriptionException` internally and delete the now-unused `BillingResult.isOk` extension
- [x] 18.4 Rewire `SubscriptionRepository`, `SubscriptionViewModel`, and the plans screen's `@EntryPoint` to the interface, so all three stay flavor-agnostic
- [x] 18.5 Add `DevBillingGateway` in `src/dev/kotlin` — the two pinned iOS product ids at iOS pricing, `Ready` with a synthetic license code, a **delayed** `Purchased` outcome, acknowledgement, and in-memory restore. No pending/failure simulation
- [x] 18.6 Add flavor-scoped Hilt modules: `DevBillingModule` (`@Provides`, `src/dev`) and `ProdBillingModule` (`@Binds`, `src/prod`)
- [x] 18.7 Make `subscriptionPlatform` flavor-scoped — `"Android"` on prod, `"AndroidDev"` on dev — via an `internal const` in each flavor source set, so simulated entitlements stay greppable in the shared `kornerz-72fba` backend
- [x] 18.8 Update `SubscriptionRepositoryTest` and `SubscriptionViewModelTest` for the new seam; make the platform assertion flavor-resolved
- [x] 18.9 Add `DevBillingGatewayTest` in `src/testDev/kotlin` — compiles only into the `dev` variant, so the simulated path cannot regress silently
- [x] 18.10 Verify the split in the generated Hilt graph: `prodDebug`'s `SingletonC` contains no `DevBillingGateway` reference and `devDebug`'s contains no `BillingClientApi` reference
- [x] 18.11 Update `design.md` (new Decision, and the previously-rejected alternative is now amended) and the `play-billing` spec with the gateway requirement
- [x] 18.12 Verify: `./gradlew test` (504 tests green across both flavors), `:app:lintDevDebug`, `:app:assembleDevDebug`, `:app:assembleProdDebug`, `:feature:subscription:assembleDevDebugAndroidTest`, plus a clean-install launch on the API 35 emulator with no `FATAL EXCEPTION`
## 19. Mandatory paywall (product amendment: the app is unusable without a subscription)

Supersedes the "close affordance"/"dismissed latch" clauses added in §11 and amended in §17: the paywall is now **not dismissible**, and the success screen unwinds past it instead of onto it.

- [x] 19.1 Remove the close affordance from `SubscriptionContent` — the trailing `Box`, the `Icons.Filled.Close` icon, the `CLOSE_DESCRIPTION` constant, the `onCloseTap` parameter on both `SubscriptionContent` and `LoadedPlans`, and the now-unused `statusBarsPadding` / `size` / `width` / `Icon` / `Icons` imports. `LoadedPlans` loses its `Box` wrapper and the scroll `Column` becomes the root
- [x] 19.2 Swallow the system back gesture in `SubscriptionScreen` — `BackHandler { }` replaces `BackHandler { viewModel.onCloseTap() }`, so the plans route can never be popped by the user. Stays enabled mid-purchase so the Play sheet's own dismissal cannot route out through here
- [x] 19.3 Drop the now-unreachable dismissal path: `SubscriptionNavEvent.Dismissed`, `SubscriptionViewModel.onCloseTap()`, and the `Dismissed ->` branch in `app`'s `SubscriptionRoutes.kt`
- [x] 19.4 Fix the success unwind — `SubscriptionSuccessNavEvent.Dismissed` now pops `SubscriptionRoute.Plans.route` **inclusively**, removing both `subscription/plans` and `subscription/success` and landing on the pre-gate destination (the bottom-bar tab screen). Falls back to a single `popBackStack()` if the plans entry is gone (e.g. after a process restore). The previous plain `popBackStack()` popped one entry and returned the user to the paywall they had just paid to leave
- [x] 19.5 Keep `PremiumGate.markDismissed()` on the gate's surface so clause 5 stays enforceable, but note in the `premium-gate` spec that no production call site arms it
- [x] 19.6 Amend `premium-gate/spec.md` — "The paywall can be dismissed once per process" becomes "The paywall is not dismissible"; the gate-navigation requirement mandates the inclusive pop; the clause-5 table row and the "No destination-change gate" scenario no longer mention dismissal
- [x] 19.7 Amend `subscription-plans/spec.md` — no close affordance, the success "Done" behaviour, and the `SubscriptionNavEvent` contract block no longer declares `Dismissed`
- [x] 19.8 Update `SubscriptionContentTest` — `theCloseAffordanceIsReachable` becomes `thereIsNoCloseAffordance` (asserts no node carries the "Close" content description), harness drops `onCloseTap`
- [x] 19.9 Update `SubscriptionViewModelTest` — the one-shot-event case asserts `OpenPrivacyPolicy` and `OpenTermsOfUse` then `expectNoEvents()`
- [x] 19.10 Verify: `./gradlew test` green, `:app:assembleDevDebug` green

### 19.x Known remaining gap (not in scope)

The Privacy Policy link on the plans screen still pops the paywall in order to reach `profile/privacy-policy`, which is hosted on the Profile tab's nested `NavHost` and is not composed while `subscription/plans` is the current destination. A free user can therefore read the policy and back out into the un-gated app, and — because `ShellViewModel.onShellResumed()` has no caller, so the gate's second call site is unwired — is not re-gated for the rest of the process. Closing this needs a decision on where the paywall re-arms after the excursion (wire `onShellResumed` to a real `ON_RESUME`, or re-present on the policy destination's pop), which is a navigation-design change rather than part of this amendment.
