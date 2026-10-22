## Context

Phases P0–P7 shipped a populated Home, Kornerz, Nooks, Loops, Profile, Notifications, and Admin-communities surface on the shared Firebase backend (`kornerz-72fba`), plus the full signup / login / session / force-update / FCM / privacy-policy / avatar-upload / edit-basic-info / create-community-and-admin plumbing. The `User` model has carried four premium fields since P1 (`core/model/.../User.kt:38-41`) but they are **dead**: `FirestoreAuthApi.signUp` writes `isUserPremium: false` on signup, `FirestoreAuthApi.fetchUser` (`:190`) reads it back, and **nothing else in the codebase touches any of them**. `UserFirestoreApi.toUserOrNull()` — the profile read path — maps none of them. `com.android.billingclient` is absent from the entire repository (0 hits for `billingclient` / `BillingClient` / `play.core` outside build artifacts). There is no subscription screen, no paywall, no premium gate, and no way for a user to subscribe.

Phase 8 closes that gap by porting the iOS Premium / IAP vertical onto Compose with Google Play Billing substituted for StoreKit.

iOS (`kornerz/Kornerz/InAppPurchase/SubscriptionController.swift`, `SubscriptionScreen.swift`, `SubscriptionSuccessScreen.swift`, `SubscriptionViewModel.swift`, `InAppManager.swift`, `kornerz/Kornerz/Models/User.swift`, `kornerz/Kornerz/Backend/Repositories/UserRepository.swift`, `kornerz/Kornerz/SwiftUI/TabView/AppTabController.swift`, `kornerz/Kornerz/User/LoginVC/LoginVC.swift`) is the source of truth for behavior, fields, string values, plan catalog, and gate conditions. The architectural baseline from P7 is unchanged: MVVM + Repository + UDF, `AsyncValue<T>` per section, `Channel<NavEvent>` for navigation, Hilt scopes (`@HiltViewModel` for VMs, `@Singleton` for repositories/APIs, pure constructor injection with no `@Binds` outside `NetworkModule`), `suspend fun … = runCatching { … await() }` for every wrapper, Coil for images, and the standard `feature/*` Gradle scaffolding.

The new additions are one feature module (`feature/subscription`), three screen-level VMs, one Billing client wrapper, one repository, two Firestore/callable APIs, four model types, one gate singleton, two shared UI components, and shell wiring for two new top-level routes. **One new third-party dependency** is introduced (`com.android.billingclient:billing-ktx`); everything else is reused.

Five iOS audit findings materially shaped the scope and are recorded in `proposal.md` § "iOS audit findings": the absence of any Profile-menu subscribe row (audit 1), the dead `Constants.trialPeriodInSeconds` (audit 2), the three-field write in `UserRepository.setUserPremium` (audit 3), the absence of any Cloud Functions source in this repository (audit 4), and the absence of any deep-link/push path into the paywall (audit 5).

## Goals / Non-Goals

**Goals:**

- Port the three-screen subscription vertical: plans host (11.1) + plans view (11.2) and subscription success (11.3), reproducing iOS `SubscriptionScreen.swift` (195 lines) and `SubscriptionSuccessScreen.swift` (43 lines) structure-for-structure — `#68B6CC` background, 320-pt top image band, "Welcome {nickname}" with `"Klouder"` fallback, the feature blurb, the conditional free-trial line, exactly two white rounded-24 plan cards with a 2-pt black selected stroke, the selected-price caption, the black-capsule "Try It Free"/"Subscribe" button, "Restore Purchase", the privacy-policy / term-of-use row, and the checkmark-seal success screen.
- Introduce the Google Play Billing v7.x integration via a single `BillingClientApi` wrapper that owns the client lifecycle, the `BillingClientStateListener`, and the Play license check, exposing a suspend/`Flow`-based surface so no VM ever touches `BillingClient` directly.
- Mirror iOS `InAppProduct` 1:1 with product ids `video_call_monthly` and `video_call_yearly_sub`, so `subscriptionType` is byte-identical across platforms.
- Implement the entitlement write exactly as iOS does it: a Firestore `updateData` on `Users/{email}` writing **three** fields (`subscriptionExpiryDate`, `subscriptionType`, `subscriptionPlatform = "Android"`), with the identical-expiry no-op guard, and premium **derived** from `subscriptionExpiryDate > now` rather than read off `isUserPremium`.
- Reproduce iOS's four-clause paywall gate (`AppTabController.showSubscriptionScreen()` `:209-229` and `LoginVC.showSubmissionScreen()` `:219-232`) at both firing points, plus a per-process dismissal latch.
- Close the `UserFirestoreApi.toUserOrNull()` read-path gap so the four premium fields round-trip through the profile read.
- Add `SessionRepository.updateUser(transform)` so the cached session user refreshes after a purchase without needing a live auth token.
- Pass the per-phase exit checklist (assembleDevDebug, installDevDebug on API 26 + API 35 emulators, no crashes, lint, unit tests, screenshots, demo walkthrough, `phase-8-end` tag).

**Non-Goals:**

- Video Calling — Vonage / WebRTC (P9), Ranking + Deep Links + Polish (P10).
- Authoring the RTDN Cloud Function or any Play Console configuration. There is **no `functions/` directory, no `*.ts`, no `firebase.json`** anywhere in this repository; P6's `deleteUserAccount` callable is likewise external and referenced only through a client. P8 ships the callable client and the verification contract, exactly as P6 did.
- The missing `subscription_bg` image asset. iOS references it at `SubscriptionScreen.swift:29` but it is **not in the iOS asset catalog** — the iOS screen renders blank there today. Android ships a vector/solid equivalent rather than reproducing a broken bitmap.
- The `subscription_features.json` "what's included" unlock list. It is loaded into `SubscriptionViewModel.featureFile` (`SubscriptionViewModel.swift:40-50`) but **never bound to any view** — dead in iOS. No equivalent ships.
- The dead strings `already_have_subscription`, `subscribe_acknowledge_msg`, `be_subscriber`, `exclusive_benifits_for_you`, `per_month_enjoy`, `premium_to_have_access_to_features`, and `subscription_welcome_msg` — all have no live Swift reference.
- Deep-link / notification entry into the paywall. iOS `DeeplinkManager.DeepLink` (`:13-54`) has no subscription case; P10 owns deep links.
- A client-side cancel / manage-subscription surface. The P8 demo script ("Cancel, see it expire") exercises the expiry path, not a cancel button; hand-off to the Play Console's subscription management is P10 polish.
- Any premium **feature** gating beyond the paywall itself. The features premium unlocks (unlimited Nook video calls, per `subscription_feature_msg`) land in P9/P10; P8 ships the gate and the entitlement, not the gated features.
- pt-BR localization of the new screens (follow-up per `project-brief.md` §11.7 and `phases.md` Principle 7).
- Registering the `com.kornerz.kornerz.dev` application id in Play Console (an unresolved `docs/phases.md:411` open question). P8 ships a graceful-disconnect path for the dev flavor instead.
- Any analytics event on purchase (no analytics service is wired anywhere in the codebase; a follow-up can add Firebase Analytics).

## Decisions

### Decision: One `feature/subscription` module hosting `plans/`, `success/`, `navigation/`, and `data/`

A single Gradle module hosts the entire P8 vertical, organized as `feature/subscription/plans/` (screens 11.1 + 11.2), `feature/subscription/success/` (11.3), `feature/subscription/navigation/` (`SubscriptionRoute`), and `feature/subscription/data/{billing,network,}`. This mirrors the per-tab boundary established in P2/P3/P4/P5/P7 and keeps the Billing wrapper and the Firestore/callable APIs local to the module. The plans and success screens share one background color and one button family, so splitting them across two modules would duplicate `KornerzCapsuleButton` for two call sites.

**Alternatives considered:**

- Two separate Gradle modules (`feature/subscription/plans` + `feature/subscription/success`). Rejected: 11.3 is 43 lines of iOS and roughly 80 lines of Kotlin; it has no repository dependency and no data layer. A module boundary for it would add a 15th Gradle module and an `implementation` edge for no decoupling win.
- Billing client in `core/network` alongside the Firestore APIs. Rejected: `core/network` is a `firebase-*`-shaped module; Play Billing is a Play-services-shaped dependency with a different lifecycle (a long-lived `BillingClient` connection vs. stateless `suspend` reads). Mixing them would drag the Play dependency onto every module that depends on `core/network`. Feature-local keeps it on exactly one module and makes the "first Play dependency" blast radius explicit.

### Decision: `BillingClientApi` is the only type that imports `com.android.billingclient`

Every Play type — `BillingClient`, `BillingClientStateListener`, `ProductDetails`, `Purchase`, `BillingResult`, `QueryProductDetailsParams` — stays inside `feature/subscription/data/billing/BillingClientApi.kt`. The wrapper projects them into Compose-free `core/model` types (`SubscriptionProduct`, `SubscriptionPlan`) or into local data classes (`PurchaseRecord`). This mirrors the existing `*FirestoreApi` wrapper convention (`feature/community/data/network/`, `feature/profile/data/network/`), where the Firebase types never escape the wrapper.

Concretely: `BillingClientStateListener` is converted to a `StateFlow<BillingConnectionState>` at the wrapper boundary, so `SubscriptionViewModel` observes a plain enum and never implements the listener. `queryProductDetails` returns `List<SubscriptionProduct>` rather than `List<ProductDetails>`.

**Alternatives considered:**

- Let the ViewModel hold the `BillingClient` directly (the pattern several Billing tutorials use). Rejected: it makes the VM untestable without a device (the codebase has no Robolectric and no Hilt test harness — verified), and it violates the repository's strict Repository/UDF boundary where VMs talk to repositories and never to SDK clients. Every P0–P7 VM goes through a repository.
- Wrap `BillingClient` behind a `BillingGateway` interface with a `@Binds` in a Hilt module. Rejected **for the initial landing**, and **accepted in the follow-up below** once it became clear that Play Billing has no development bypass and the paywall was therefore undemoable on `dev`. See "Decision: A `SubscriptionBillingGateway` seam with a `dev`-flavor simulated implementation".

### Decision: A `SubscriptionBillingGateway` seam with a `dev`-flavor simulated implementation

*(Added after the first P8 landing, in response to Play Billing having no development bypass.)*

**The problem.** `com.android.billingclient` has no sandbox. Google ships no mock mode, no local catalog, and no equivalent of the Xcode `.storekit` file that makes this problem free on iOS (`Kornerz-SKConfig.storekit`). The only Play-provided escape hatches all have the same precondition:

| Escape hatch | Precondition |
|---|---|
| License testing (Play Console → Settings → License testing) | An app must already exist in Play Console with a matching package name and upload-certificate SHA-1 |
| Internal / closed testing track | The above, plus an uploaded build and an enrolled tester account |
| Static test cards for auto-renewing subs | The above, plus the two products published on that track |

`com.kornerz.kornerz` is not registered, so on `dev` **every** Play call legitimately fails. The original landing shipped the graceful-disconnect path the task list promised, and it works exactly as specified — the plans screen renders a non-fatal "Billing is unavailable in this build" notice — but it is a paywall that cannot be demonstrated, screenshotted, or walked through, which is the entire point of the P8 exit criteria.

**The decision.** Introduce a `SubscriptionBillingGateway` interface in `feature/subscription/data/billing/` and select its implementation by flavor, mirroring `app`'s existing `env` (`dev`/`prod`) dimension:

| Variant | Binding | Behaviour |
|---|---|---|
| `dev` | `DevBillingGateway` (`src/dev/kotlin`) | Two hard-coded plans, a delayed simulated purchase, acknowledgement, in-memory restore |
| `prod` | `BillingClientApi` (`src/main/kotlin`) | Real Google Play Billing |

The split is compile-time, not runtime. `DevBillingGateway` and `DevBillingModule` live in `src/dev/kotlin` and are not even compiled into a `prod` variant, so no flag, manifest marker, or remote config can activate the simulated gateway in a shipped build. Verified in the generated Hilt component: `DaggerKornerzApplication_HiltComponents_SingletonC` for `prodDebug` contains no reference to `DevBillingGateway`, and the `devDebug` one contains no reference to `BillingClientApi`.

**The interface deliberately does not mirror `BillingClientApi`'s signatures.** `launchPurchase` returns `Result<Unit>` on the interface rather than Play's `BillingResult`, so a simulated implementation can honour the contract without importing `com.android.billingclient` at all. `BillingClientApi` remains the only file in the app that imports Play types, and it now converts the response code to a typed `SubscriptionException` internally rather than handing the raw code to callers.

**The simulated purchase writes `subscriptionPlatform = "AndroidDev"`.** `kornerz-72fba` is shared with iOS and premium is derived from `subscriptionExpiryDate` on both platforms, so a simulated subscription really would make that user read as premium in the iOS build. A distinct value makes every fake entitlement greppable with one Firestore query so they can be found and cleared when Play goes live. It is still only ever written, never branched on — the same rule `"Android"` already followed. The value is a flavor-scoped `internal const` (`src/dev/…/EntitlementPlatform.kt` = `"AndroidDev"`, `src/prod/…` = `"Android"`), so `SubscriptionRepository` stays flavor-agnostic.

**What the simulation deliberately omits.** Pending purchases (Play's deferred state has no analogue worth fabricating), purchase failures, renewal, cancellation, and refund. The first two are covered by `SubscriptionViewModelTest` against a mocked gateway; expiry is real, because `deriveExpiryFrom` turns the recorded purchase time into the same 30-/365-day schedule. `DevBillingGatewayTest` (in `src/testDev/kotlin`, so it compiles only into `dev`) holds the fake to the same standard as the real wrapper.

**Alternatives considered:**

- Register the Play Console app now and use License testing. Rejected as the *immediate* unblock: it is hours of console setup and the products do not exist yet, and it would leave the paywall untestable on any machine without the tester account enrolled.
- Strip billing out of the `dev` flavor entirely. Already rejected in the original landing, and this change makes it strictly better: `dev` now gets the full flow rather than no flow.
- Persist the simulated purchase to DataStore so it survives a process restart. Rejected: it would make the dev build's entitlement state drift from Play's actual model, where local purchase history is cleared by "clear app data" but a real purchase would be restored by Play. In-memory state plus the Firestore entitlement reproduces the real asymmetry more faithfully.
- Put the fake in `src/debug` instead of a flavor source set. Rejected: `dev` and `prod` are both built as `debug` in practice, so a build-type split would leak the simulated gateway into `prodDebug` — the build QA and PM receive.

### Decision: `suspend` + `await()` for the Billing wrapper, with no `Dispatchers.IO`

The Billing library's `QueryProductDetailsParams` / `QueryPurchasesParams` / `AcknowledgePurchaseParams` APIs are all `Task`-returning and are bridged with `kotlinx.coroutines.tasks.await`, exactly as every existing `*FirestoreApi` does. No `withContext(Dispatchers.IO)` is introduced. Play's own client already dispatches its IPC off the main thread; the `await()` bridge resumes on the caller's dispatcher, which for the VMs is `viewModelScope` (Main). This matches `core/network`, where the pattern is uniform and `withContext` appears **only** in `core/storage` (`CountryCodesRepository`, `KornerInterestRepository`).

`launchBillingFlow` is the one exception: it is not a `Task` but a `BillingResult`, delivered synchronously from `BillingClient.launchBillingFlow`. It is exposed as a plain (non-suspend) function returning `BillingResult`, and the purchase *outcome* arrives later via the `PurchasesUpdatedListener`, which the wrapper forwards into a `Channel<BillingPurchaseResult>` that the VM collects. This is the closest Play equivalent of iOS's `Product.purchase()` async call plus `Transaction.updates` listener.

**Alternatives considered:**

- Add an `@IoDispatcher` qualifier and wrap every call. Rejected: introduces a new DI qualifier and a dispatch strategy that the `core/network` layer has deliberately never used. Consistency with the existing layer wins.
- Poll `queryPurchasesAsync` after `launchBillingFlow` instead of using `PurchasesUpdatedListener`. Rejected: racy and wasteful; the listener is the purpose-built API and is Play's direct analogue of iOS's `InAppManager.listenForTransactions()` (`InAppManager.swift:111-129`).

### Decision: Premium is derived from `subscriptionExpiryDate`, and `isUserPremium` is written-but-never-read

`User.isUserPremiumNow` is `subscriptionExpiryInstant?.isAfter(Clock.System.now()) == true`. This mirrors iOS `AppHelper.isUserPremium` (`AppHelper.swift:39-45`) exactly, and it is the correct reading of iOS's behavior for two independent reasons: (a) iOS's `UserRepository.setUserPremium` **never writes** `isUserPremium` — it writes only the other three fields (`:170-175`) — so the bit goes stale on renewal and never flips on cancellation; (b) iOS's own `User.setUserPremium` mutating helper (`:86-91`) sets it to `true` locally but that mutation is only persisted through `UserDefaults`, never back to Firestore. Deriving from the expiry is the only approach that is correct for renewals, cancellations, and cross-platform reads.

The raw `isPremium` field on the Android `User` is kept (it is written at signup by `FirestoreAuthApi.signUp` and is part of the shared document shape) but is explicitly **not** the gate input. Its KDoc is corrected to say so, so a future reader does not wire the wrong field.

**Alternatives considered:**

- Write `isUserPremium: true` from Android on purchase and gate on it. Rejected: this diverges from iOS's field set, breaks on renewal (nothing flips it back to `false` on expiry, and there is no RTDN handler in this repo to do it), and creates a second source of truth that can disagree with the expiry. The proposal's cross-platform guarantee — an iOS subscriber reads as premium on Android — depends on the expiry being the single authority.
- Cache the entitlement in `DataStore` and treat it as authoritative. Rejected: a cached expiry silently goes stale across a device clock change, a Play refund, or an RTDN revocation that the app never observed. Firestore is authoritative; the session cache is a display optimization only.

### Decision: The entitlement write is a Firestore `updateData` of exactly three fields, keyed by email

`SubscriptionFirestoreApi.setSubscription(email, expiryDate, planId, platform)` issues `usersCollection.document(email).updateData(mapOf("subscriptionExpiryDate" to Timestamp, "subscriptionType" to planId, "subscriptionPlatform" to "Android"))`. Three details are non-obvious and all mirror iOS:

1. **`updateData`, not `setData`.** iOS uses `updateData` (`:171`), so a user doc missing one of the three keys is patched rather than replaced — writing with `setData` would clobber the other ~20 user fields.
2. **Keyed by email, resolved via `codeUser`.** The repo-wide convention is `Users/{email}` as the document key (`UserFirestoreApi.kt:12-27` KDoc, `:38-46` `fetchByEmail`). Not `codeUser`, and **not** the lowercase `users/{firebaseUid}` path used by `FirestoreAuthApi.updateFcmToken` (`:153-168`) — that is a pre-existing inconsistency in the FCM code, not a convention to copy.
3. **The identical-expiry no-op guard.** iOS returns early when `user.subscriptionExpiryDate == expiryDate` (`:168`). Android mirrors it so a repeated restore or a re-entrant purchase does not issue a redundant write.

`isUserPremium` is **not** written. This matches iOS's `setUserPremium` field set exactly.

**Alternatives considered:**

- Write all four fields including `isUserPremium`. Rejected: diverges from iOS's field set for no benefit (the bit is unused), and creates the two-sources-of-truth problem the previous decision avoids.
- Route the write through a Cloud Function. Tempting — it would be the only trustworthy place to do it, since the client cannot verify its own purchase. Rejected for this phase: no `functions/` directory exists in this repository, so the change would have to invent a backend that has no deployment story, no CI, and no owner. P8 does the iOS-parity client write (which is what iOS does today) **and** adds the verification callable so a future function can tighten this without changing the Android UDF contract.

### Decision: Optimistic local grant + server verification on the next launch

A purchase grants entitlement immediately on the client, from the `Purchase` object Play returns (whose `purchaseTime` + the product's billing-period details give the expiry). In parallel, the client calls `SubscriptionCloudFunctionsApi.verifyPurchase(token, productId)`; the authoritative `SubscriptionVerificationResult` is written back to Firestore when it arrives. On every subsequent app launch, `SubscriptionRepository.reconcileEntitlement()` calls the same verification endpoint before the gate is evaluated.

This mirrors iOS, where `InAppManager.checkVerified` performs **local JWS verification only** (`InAppManager.swift:91-101`) and there is no server-side verification at all (audit finding: no App Store Server API call exists in the tree). Android ships iOS's behavior **plus** the server round trip that `docs/project-brief.md:223` and `docs/phases.md:260-261` call for — an additive improvement, not a divergence, and it is what makes the `docs/project-brief.md:264` RTDN-delay risk manageable.

**Alternatives considered:**

- Block the success screen until the server confirms. Rejected: it makes the demo dependent on a Cloud Function that lives outside this repo and is therefore not guaranteed to be deployed. A `PurchaseSucceeded` navigation that can fail because of an unrelated backend outage is a worse product and a worse demo.
- Skip the server round trip entirely this phase (iOS parity only). Rejected: `docs/phases.md:260-261` explicitly lists RTDN + server-side verification as P8 deliverables, and `project-brief.md:21` requires "platform-aware tracking" for cross-platform premium. The graceful-degradation design (optimistic grant, server-authoritative reconcile) delivers both without coupling the UI to backend availability.

### Decision: `SessionRepository.updateUser(transform)` rather than re-writing the whole session

The existing write path is `setSession(token, expiresAt, user)`, which requires a live auth token that the billing flow does not have and must not need. P8 adds `suspend fun updateUser(transform: (User) -> User): Result<Unit>`, which reads the current `SessionRecord`, applies `transform` to the `user` field only, and writes the record back with `token` and `expiresAt` preserved verbatim inside the same `dataStore.updateData` block — so the write stays atomic and the auth-lifecycle invariants (anonymous detection via empty token / empty user id / past expiry, in `SessionRepository.toState()`) are untouched.

**Alternatives considered:**

- Add a separate `DataStore` key for cached entitlement. Rejected: `SessionRecord` is a single `@Serializable` JSON document; adding a second preference key means a second serialization path and a new partial-failure mode (premium cached but user stale). One document, one write.
- Do not cache the entitlement at all; always re-read Firestore on every gate evaluation. Rejected: the gate evaluates on every shell resume; a Firestore round trip per resume adds latency to the tab-switch path for a value that changes at most once per billing period.
- Route the refresh through `UserProfileRepository.inMemoryUser` (the P6 in-memory cache). Rejected: that cache is scoped to `feature/profile` and is not observed by the shell or the gate; making the gate depend on a profile-module in-memory field couples two features through a third.

### Decision: The paywall gate fires from exactly two places, mirroring iOS

`PremiumGate` reproduces the four-clause iOS guard — already-open, logged-in, no active Play entitlement, `isUserPremium == false` — plus a fifth clause, `wasDismissedInThisSession`, which iOS gets implicitly from `isSubscriptionOpened()` inspecting `presentedViewController`. The gate is evaluated from two call sites, both of which have a direct iOS counterpart:

- **Post-login**, in `feature/auth`'s success path, mirroring `LoginVC.showSubscriptionScreen()` (`:219-232`).
- **On shell resume / entitlement re-emit**, in `ShellViewModel`, mirroring `AppTabController`'s `CombineLatest` sink (`:170-189`) which calls `showSubscriptionScreen()` on every products-or-user emission.

`markSatisfied()` is called when a purchase completes or an active entitlement is detected, so the gate cannot re-fire mid-session and lock the user out of the app behind a paywall they already paid for.

**Superseded by §19** (`tasks.md`): the paywall is mandatory, because the product requires a subscription before the app is usable. The plans screen therefore renders **no** close affordance and swallows the system back gesture, and `markDismissed()` has no production call site — it stays on `PremiumGate`'s surface only so clause 5 remains enforceable. The success screen unwinds past the plans route inclusively rather than onto it, since the plans route can no longer be popped by the user.

The earlier reasoning below recorded the opposite call — that "a paywall that a free user cannot dismiss is a retention hazard and a store-review rejection risk, so the plans screen always has a working close affordance that calls `markDismissed()`" as a deliberate Android-only improvement over iOS. That has been reversed by the product decision. iOS has no working close affordance either (`SubscriptionController.customBackButton()` is commented out at `:22`), so the new behaviour is the parity-preserving one.

Because the plans route can no longer be popped, the success screen's dismissal cannot use a single `popBackStack()`: that lands the user back on `subscription/plans`. It pops `SubscriptionRoute.Plans.route` inclusively instead, removing both subscription routes and returning the user to the pre-gate destination.

**Alternatives considered:**

- Fire the gate on every `NavHost` destination change. Rejected: it would re-present the paywall on every tab switch for a free user, which is both hostile and unlike iOS.
- Gate on `Constants.trialPeriodInSeconds`-style local timer (`user.isUserPremium == false && now > trialEnd`). Rejected outright: that expression **does not exist in iOS** (audit finding 2). `Constants.trialPeriodInSeconds` (`Config.swift:117`) has zero readers, and the trial is App Store Connect's introductory offer, which on Android is Play's free-trial offer on the base plan. The 7-day value is carried into `core/model` as documentation parity only and is never branched on.
- Persist a dismissal across app launches. Rejected: iOS's gate has no such latch (it re-presents on every launch for a free user), and a permanent dismissal would suppress the paywall permanently without any upgrade path being surfaced.

### Decision: The "Premium" Profile row is Android-only, and `ProfileMenuRow` gains a `trailingText` slot

iOS has **no** subscribe entry point in `UserProfileVC.setupMenuList()` (`:75-110`) and no premium case in `TypeOfUserInfoCell` (audit finding 1). Android adds a "Premium" menu row anyway: it is a visible, always-reachable subscribe entry point for a returning subscriber who wants to see or change their plan, and the only way back to the plans screen once the gate is satisfied. The row is placed between "My Data" and the conditional "My Communities" row and is always visible.

The row's trailing edge shows the current premium state (`Premium` / `Monthly` / `Yearly` / `Active until {date}`). `ProfileMenuRow` currently accepts only `iconRes`, `title`, `onClick`, `modifier`, so this is the **first shared-component signature change** P0–P7 has required; `trailingText: String? = null` is added as an optional parameter with a null default, so all seven existing call sites compile unchanged.

**Alternatives considered:**

- No Profile row; the gate is the only entry point. Rejected: once the gate is satisfied (§19) there would be no way back to the plans screen at all, and the "see premium features unlock" demo loses its natural home. Also, a paywall with no visible subscribe affordance in a settings menu is a common App Store review complaint.
- Reuse the conditional "My Communities" pattern (row hidden when `> 0`) inverted — i.e. hide the row for premium users. Rejected: premium users still need a route to see their plan and expiry, and a row that disappears after purchase is disorienting. The row stays always visible with a changed trailing label.
- Add a new `PremiumMenuRow` composable instead of extending `ProfileMenuRow`. Rejected: it would duplicate the 73-pt rounded-16 card, the 24-pt icon, and the 16-sp bold title styling purely to add one optional text. Extension is one parameter; duplication is ~40 lines.

### Decision: Two new `core/ui` components — `KornerzCapsuleButton` and `SubscriptionPlanCard`

`core/ui` currently has `KornerzButton(Filled | Outlined | Text)` at 52 dp, and **no** capsule variant, no card component, no `TopAppBar`, no `Scaffold`, no `RadioButton`/`Switch` (verified by grep across `core/ui`). The plans screen needs two things the kit does not have: the black-capsule / white-capsule full-width buttons that iOS gets from `KornerzButton.capsuleWhiteOnBlack` and `.capsuleBlackOnWhite` (`KornerzButton.swift:60-84`, `:263-277`, `:279-294`), and the selectable white rounded-24 plan card.

`KornerzCapsuleButton` is added to `core/ui` (not local to `feature/subscription`) because it is a brand-level primitive with two color variants, matching how `KornerzButton` is treated. `SubscriptionPlanCard` stays **local** to `feature/subscription` because it is the only selectable card in the app and has no reuse case; the unselected/selected variants are two arguments on one composable, not two components.

**Alternatives considered:**

- Reuse `KornerzButton` with `style = Filled` and a capsule `RoundedCornerShape(50)`. Rejected: `KornerzButton` hardcodes its height to 52 dp, its shape to `KornerzShapes.large` (16 dp), and its filled variant to the theme primary color. Retrofitting all three would change the button used by every P0–P7 screen — a far larger blast radius than adding a new primitive.
- Add `SubscriptionPlanCard` to `core/ui`. Rejected: it embeds Play-catalog-specific concepts (trial offer tag, per-month equivalent line) that no other screen has. `core/ui` stays free of feature vocabulary, as it is today (its components take primitives and `AsyncValue`, never a domain model other than `Korner`).
- Use Material 3's `Card` + `Surface` directly. Rejected: the codebase's visual language is explicit hex values mapped from iOS `Config.swift` (`C353446`, `C68B6CC`, `CF2F4F6`, `C9E9EAE`) rather than Material 3 tonal elevation. A raw `Card` would drift from every other screen in the app.

### Decision: `SubscriptionExpiryDate` stays a `String?` on `User`, parsed with kotlinx-datetime

`User.subscriptionExpiryDate` is already `String?` on the Android model, and `SessionRecord` serializes the whole `User` into a single DataStore JSON document — so a non-string field would need a custom serializer. iOS stores a Firestore `Timestamp` (`User.swift:30`). The Android port keeps the Kotlin-side type as `String?` (matching P1–P7's convention of storing timestamps as strings and parsing at the boundary) and adds `User.subscriptionExpiryInstant: Instant?`, which returns `null` on an unparseable value rather than throwing. The Firestore write path converts to a `Timestamp` on the way out via `SubscriptionFirestoreApi`, so the on-wire document shape is byte-identical to iOS's.

**Alternatives considered:**

- Change the field to `Timestamp?` / `Instant?`. Rejected: it breaks `SessionRecord`'s `@Serializable` round-trip (Firestore `Timestamp` is not `@Serializable`) and diverges from every other timestamp on the model (`createdAt: String?`, `SessionRecord.expiresAt: String`).
- Store epoch millis as a `Long`. Rejected: diverges from iOS's `Timestamp` on the wire, so a cross-platform read would need a type discriminator. ISO-8601 strings parse unambiguously and match the repo's existing `expiresAt` convention.

### Decision: The dev flavor ships a graceful-disconnect path instead of a Play-error path

The `dev` flavor has `applicationIdSuffix = ".dev"`, so its application id is `com.kornerz.kornerz.dev`, which is not a registered Play listing. `BillingClient.newBuilder(context)` calls `enablePendingPurchases`/`isReadyAsync` and the license check against that id, which returns `BillingResultCode.DEVELOPER_ERROR` or fails the license check. P8 maps that to `BillingConnectionState.Disconnected` with a typed reason and renders a non-fatal notice on the plans screen ("Billing is unavailable in this build") instead of crashing or showing an infinite spinner. The `prod` flavor is the one that talks to real Play.

**Alternatives considered:**

- Strip the billing feature out of the `dev` flavor via a source set / flavor-specific dependency. Rejected: the demo and QA builds run on `dev` (`docs/phases.md:412` proposes an Internal Testing track, which would be a third application id), so a dev build with no paywall cannot demonstrate the P8 exit criteria at all.
- Crash loudly on an unplayable application id so the misconfiguration surfaces immediately. Rejected: it would break the entire app for anyone running the standard `assembleDevDebug` build that CI produces (`.github/workflows/android-pr.yml` builds only `assembleDevDebug`). A non-fatal notice on one screen is the correct blast radius.

### Decision: Verify against the certificate, not a hard-coded license string

`app/build.gradle.kts` uses the debug signing config for both `debug` and `release` build types (`signingConfig = signingConfigs.getByName("debug")`, `isMinifyEnabled = false` in both). Play Billing's license check compares the signing certificate fingerprint against the one registered in Play Console. Because this project is unsigned-by-P8 and both build types share the debug keystore, the license check result is environment-dependent and cannot be asserted in a unit test. `BillingClientApi` therefore surfaces the license-check result as part of `BillingConnectionState` rather than gating on it, and the `prod` release-signing strategy remains an open question (it is already open question 3 in `docs/phases.md:407`).

## Risks / Trade-offs

- [The Cloud Function, its Google Play service-account credentials, and the RTDN Pub/Sub subscription live outside this repository — no `functions/` directory exists here] → Mitigation: P8 follows the P6 `deleteUserAccount` precedent exactly. The Android side ships `SubscriptionCloudFunctionsApi` + the `SubscriptionVerificationResult` contract, the entitlement write is optimistic so the flow completes with the function absent, and `reconcileEntitlement()` degrades to "trust the local expiry" when the callable is unavailable. The `subscription-verification` spec states the boundary explicitly so whoever deploys the function has a written contract to build against.
- [RTDN delivery can lag by minutes, so a cancelled or refunded subscription may show as active on-device until the next server round trip] → Mitigation: this is the `docs/project-brief.md:264` risk, mitigated as documented there. Concretely: (a) the gate always evaluates against `subscriptionExpiryDate`, not a device-local flag, so expiry is enforced locally regardless of RTDN; (b) `reconcileEntitlement()` runs on every app launch and overwrites the Firestore fields with the server's verdict; (c) a revoked entitlement flips `isUserPremiumNow` to `false` on the very next read because the derivation is a pure comparison against `now`, with no cached boolean to go stale.
- [The `com.kornerz.kornerz.dev` application id is not a registered Play listing, so `dev` builds cannot complete a purchase] → Mitigation: mapped to a typed `BillingConnectionState.Disconnected` reason and a non-fatal notice on the plans screen; the rest of the app is unaffected and CI (`assembleDevDebug`) keeps passing. `prod` builds are the demo target. Registering a Play listing for the dev id remains an open question owned by `docs/phases.md:411`.
- [Play Console product ids, base plans, and prices are not under version control, so a misconfigured console yields an empty catalog or wrong prices with no build-time signal] → Mitigation: `queryProductDetails` returns an empty list rather than throwing, which renders the plans screen's "no plans available" error state with a retry. The two product ids (`video_call_monthly`, `video_call_yearly_sub`) are pinned in `SubscriptionProductType` and covered by a unit test, so an accidental rename fails CI even though the console state cannot be.
- [Cross-platform entitlement reads depend on both platforms writing the same `subscriptionType` raw values and the same ISO-8601 `subscriptionExpiryDate` format] → Mitigation: `SubscriptionProductType` raw values are byte-identical to iOS `InAppProduct` and covered by a unit test; `User.subscriptionExpiryInstant` returns `null` (not a thrown exception) on an unparseable string, so a malformed value from the other platform degrades to "free" rather than crashing. Documented in the `premium-entitlement` spec.
- [`UserFirestoreApi.toUserOrNull()` currently drops all four premium fields, so until that mapping lands every read returns "free"] → Mitigation: this is an explicit P8 task (`UserFirestoreApi.toUserOrNull()` maps the four fields) with a unit test asserting the round-trip, not an implicit follow-up. The phase is not complete until the Profile screen renders the trailing premium label from a real read.
- [The plans screen depends on `core/ui` gaining a capsule button, and a mis-styled capsule diverges visibly from iOS] → Mitigation: `KornerzCapsuleButton` takes its colors and typography from the existing `KornerzColors` / `KornerzTypography` tokens, and `SubscriptionPlanCard` uses the explicit iOS hex values recorded in the `subscription-plans` spec table. `@Preview` composables for both are a required task, and P8's exit checklist requires one screenshot per in-scope screen.
- [Acknowledgement failures leave a purchase in an unacknowledged state that Google refunds after three days] → Mitigation: `acknowledgePurchase` is called before the entitlement is granted, and an acknowledgement failure surfaces as `SubscriptionError.NotAcknowledged` and **blocks** the success navigation rather than being swallowed. This is the one place the Android port is deliberately stricter than iOS (which has no acknowledgement concept at all — that is StoreKit's job).
- [The paywall gate adds a per-process latch whose behavior differs from iOS's `presentedViewController` inspection] → Mitigation: the latch is `wasDismissedInThisSession` scoped to the process and reset on logout-and-login within the same process, matching iOS's "re-present after a fresh login" behavior. It is specified in the `premium-gate` spec with the two firing points named, so the divergence from iOS's implicit mechanism is documented rather than incidental.
- [Play Billing v7 raises the effective `compileSdk`/AGP floor in some configurations] → Mitigation: the project is already on `compileSdk = 35` / AGP 8.5.2 / Kotlin 2.0.21, comfortably above Billing v7's requirements. `minSdk = 26` is above Billing's floor of 21. The dependency-resolution risk is confined to a single version-catalog entry.
- [Release builds are signed with the debug keystore and `isMinifyEnabled = false`, so the release variant cannot be uploaded to Play] → Mitigation: pre-existing condition, not introduced by P8. Flagged as an open question below; P8's exit criteria are met with a `dev` build plus a Play-side note, and the signing strategy is tracked as `docs/phases.md:407` open question 3.
- [`isUserPremium` remains a write-never-read field on the Android model, which is confusing to a future reader] → Mitigation: the field's KDoc is corrected in this phase to state explicitly that it is a shared-document compatibility field and that `isUserPremiumNow` is the gate input. The `premium-entitlement` spec carries the same warning as a requirement.

## Migration Plan

Phase 8 lands as a single PR adding the new feature module, the model projections, the Billing wrapper, the two data APIs, the repository, the three screen VMs, the two `core/ui` components, the gate, the Profile row, and the shell wiring. **There is no data migration.** The three Firestore fields already exist on every `Users/{email}` document (written as absent by default; `isUserPremium` is written `false` at signup by `FirestoreAuthApi.signUp`), so the entitlement write is an in-place `updateData` on existing documents and needs no composite index. The plans screen reads nothing from Firestore at all — the catalog comes from Play — so the Firestore read-cost impact of this phase is one extra field-map per profile read.

Rollback is a `git revert` of the merge commit and re-tagging `phase-7-end`. Because the only persistent side effect is three scalar fields on `Users/{email}`, a rollback leaves at most some users with `subscriptionPlatform: "Android"` and no Android client to honor it — which is exactly the state the iOS app already tolerates, since it derives premium from the same `subscriptionExpiryDate` regardless of platform. No rollback cleanup step is required.

One operational prerequisite sits outside the repository: the two Play Console products (`video_call_monthly`, `video_call_yearly_sub`) must exist in a subscription group with a free-trial offer on the base plan before the `prod` build can complete a purchase. This is not blocking for the build, only for the live demo.

## Open Questions

1. **Play Console app + subscription setup** — is `com.kornerz.kornerz` a paid or free app, and are the two products created with a 7-day free trial on the monthly base plan (mirroring `Kornerz-SKConfig.storekit:185-200`)? This is `docs/phases.md:411` open question 6 and it gates the live demo, not the build. Current decision: assume the products exist per iOS pricing (monthly `10.99`, yearly `95.9`) and that the trial is configured in the console; the Android code reads the trial from Play's `freeTrialOfferDetails` rather than hard-coding it.
2. **Cloud Function ownership and deployment** — who owns the `verifySubscription` callable and the RTDN Pub/Sub topic, and where do they live? No `functions/` directory exists in this repo. Current decision: ship the client + contract (P6 `deleteUserAccount` precedent) and let the function be deployed alongside the existing iOS infrastructure.
3. **Release signing** — the upload keystore and the Play-registered certificate fingerprint. Both build types currently use the debug keystore with `isMinifyEnabled = false`, so no release build can be uploaded as-is. This is `docs/phases.md:407` open question 3 and it blocks any real Play submission (but not the P8 exit checklist). Current decision: out of scope for P8; flagged for the phase that does Play submission.
4. **Which application id does QA use?** — `docs/phases.md:412` proposes an Internal Testing track, which would be a third application id alongside `dev` and `prod`. If QA builds come from `dev`, the paywall demo cannot complete a purchase there. Current decision: the P8 demo runs on `prod`; `dev` renders the non-fatal "billing unavailable" notice.
5. **Is a purchase-restore flow required for the "cancel, see it expire" demo?** — iOS exposes "Restore Purchase" and Play's equivalent is `queryPurchasesAsync(SUBS)`, which P8 ships. Whether QA should instead exercise cancellation by letting a short trial lapse (the current approach) or by an actual Play Console cancellation is a PM/QA decision. Current decision: ship restore; demo cancellation via the trial/expiry path.
6. **Does the free trial apply to both plans or only the monthly?** — iOS's `.storekit` config gives **both** products a `P1W` free introductory offer (`Kornerz-SKConfig.storekit:185-190` and the equivalent block for yearly). That is a Play Console configuration, not code. Current decision: the Android UI derives trial availability per-product from Play's `freeTrialOfferDetails`, so whichever plan the console configures with a trial shows "Try It Free"; no code change is needed if the console differs from iOS.