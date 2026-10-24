## 1. Module scaffolding + dependencies

- [x] 1.1 Create `feature/ranking/` with `build.gradle.kts` mirroring `feature/profile/build.gradle.kts` — `namespace = "com.kornerz.feature.ranking"`, compileSdk 35, minSdk 26, JVM 17, Compose, Hilt, kapt `correctErrorTypes = true`, `implementation(project(":core:ui" | ":core:model" | ":core:network" | ":core:storage"))`, test stack (`junit`, `mockk`, `turbine`, `kotlinx-coroutines-test`) — see `ranking-leaderboard` spec
- [x] 1.2 Create the package structure `feature/ranking/{list,list/components,navigation}`
- [x] 1.3 Wire `:feature:ranking` into `settings.gradle.kts`; add `implementation(project(":feature:ranking"))` to `app/build.gradle.kts` **and** to `feature/shell/build.gradle.kts` (the shell owns the tab graph) — see `android-build` spec
- [x] 1.4 Confirm **no** per-module `repositories {}` block is added — `settings.gradle.kts` uses `RepositoriesMode.FAIL_ON_PROJECT_REPOS`
- [x] 1.5 Add `archunit = "1.3.0"` to `[versions]` and `archunit-junit4 = { group = "com.tngtech.archunit", name = "archunit-junit4", version.ref = "archunit" }` to `[libraries]` in `gradle/libs.versions.toml`; add `testImplementation(libs.archunit.junit4)` to `app/build.gradle.kts` — see `android-build` spec. **If the artifact cannot be resolved in this environment, skip this task and take the §11.6 fallback** — the artifact *is* resolvable, so §11.6 is not needed. **Coordinate correction:** the group id is `com.tngtech.archunit`, not `com.tarchy.archunit`; the latter was ArchUnit's pre-1.0 group and publishes no 1.x artifact, so the originally-specified `com.tarchy.archunit:archunit-junit4:1.3.0` 404s on Maven Central. The artifact name and version are unchanged.
- [x] 1.6 Run `./gradlew :feature:ranking:assembleDevDebug` on the empty scaffold — should succeed before any code lands

## 2. Domain model — `core/model`

- [x] 2.1 Add `Gamification` (`@Serializable`, Compose-free) to `core/model/Gamification.kt` with the exact iOS `@SerialName` mapping — `gameficationId`, `gameficationAction`, `gameficationDateHora`, **`gameficationQtdPts`**, `gameficationUserId`, `gameficationReferenceId` — see `ranking-leaderboard` spec
- [x] 2.2 Add the top-level `Rank` enum to `core/model/Rank.kt` with exactly `GOLD`, `SILVER`, `BRONZE`, `NONE`
- [x] 2.3 Replace `UserRanking.Medal` with `Rank` in `core/model/UserRanking.kt` — keep `userId` / `userName` / `points`, keep `@SerialName("madal")` on the `medal` property, change its type to `Rank` with a `Rank.NONE` default. Update the KDoc, which currently says "the P10 polish phase will surface this on a leaderboard screen"
- [x] 2.4 Add the `DeepLink` sealed interface to `core/model/DeepLink.kt` with the seven cases `NewEvent`, `NewArticle`, `NewForum`, `VideoCall`, `NewComment`, `NewLoop`, `NewLoopComment` — see `deep-link-routing` spec
- [x] 2.5 Implement `DeepLink.toUri()` and `DeepLink.parse(uri)` against the `kornerz://` grammar — `kornerz://loop/{id}`, `kornerz://forum/{id}`, `kornerz://community/{id}/call`, `kornerz://community/{id}/event/{id}`, `kornerz://community/{id}/article/{id}`, `kornerz://community/{id}/forum/{id}`
- [x] 2.6 Add `Community.communityId: String? = null` mapped to the `communityId` field in `core/model/Community.kt` — nullable with a `null` default so no existing construction site changes. Do **not** change the meaning of `Community.id` or `Community.code` — see `user-notification-domain` spec
- [x] 2.7 Fix `UserNotificationDeeplinkType.fromRaw` in `core/model/UserNotification.kt` to parse the iOS raw values (`NewEvent`, `NewArticle`, `NewForum`, `VideoCall`, `NewComment`, `NewLoop`, `LoopComment`) **case-insensitively** — see `user-notification-domain` spec
- [x] 2.8 Change `UserNotification.deeplink(with: Community?)` to return `DeepLink?` (was `String?`), honouring iOS's per-case field requirements and **not** porting iOS's `newComment → videoCall` bug. Use `community.communityId`, falling back to `community.id` when it is null
- [x] 2.9 Confirm the `user-notification-domain` spec's stale "P10 polish will correct it" note is now normative — no code change needed beyond 2.8

## 3. Data layer — gamification

- [x] 3.1 **Blocking fix**: in `core/network/.../gamification/GamificationRepository.kt`, change `FIELD_POINTS` from `"gameficationPoints"` to **`"gameficationQtdPts"`**. The current value matches no server field, so every score reads `0` — see `ranking-leaderboard` spec
- [x] 3.2 Add a fallback read of `"gameficationPoints"` used **only** when `gameficationQtdPts` is absent on a document; `gameficationQtdPts` SHALL win when both are present — see the design's risk section
- [x] 3.3 Rewrite `getRanking(count)` to follow iOS `GamificationService.getRanking` (`:106-140`): range query on `gameficationDateHora`, `startAfter` pagination, per-user aggregation of `gameficationQtdPts`, sort by points descending, truncate to `count`
- [x] 3.4 Add the missing name-resolution step to `getRanking`: one batched `Users.whereEqualTo("codeUser", in: userIds)` query assigning `User.nickname` to each row's `userName`, keeping the literal `"User"` fallback for an id that does not resolve — see `ranking-leaderboard` spec
- [x] 3.5 Assign the `Rank` tier by final sorted position (gold / silver / bronze for the first three, `NONE` after), replacing the current inline `UserRanking.Medal` mapping
- [x] 3.6 Keep the sort **stable** so equal-point rows keep their first-seen order, and document the divergence from iOS's unstable sort in the KDoc
- [x] 3.7 Confirm `getUserPoints()` returns `Result.success(0)` for an anonymous session and for a user with no `codeUser`, and that it does not query Firestore in those cases
- [x] 3.8 Confirm `getRanking` returns `Result.failure` (never throws) when the underlying query fails

## 4. Data layer — community lookup

- [x] 4.1 In `core/network/.../community/CommunitiesRepository.kt`, change `fetchById(id)` from `collection.document(id)` to `whereEqualTo("communityId", id)` — matching iOS `CommunitiesRepository.swift:241` and `APICommunity.swift:475`. The `communityId` field and the document id are different values — see `deep-link-routing` spec
- [x] 4.2 Populate the new `Community.communityId` field in `fetchById`, `fetchByName`, and `fetchCommunityNamesByIds`. **Do not** change what `Community.id` and `Community.code` mean in those methods — **note:** `fetchCommunityNamesByIds` returns a `Map<String, String>` of ids to names and constructs no `Community`, so there is no field to populate there; the third `Community`-constructing method in the repository is `fetchFollowingCommunities`, and the field is populated there too
- [x] 4.3 Confirm the existing P6 notification path improves as a side effect: `NotificationsViewModel.openNotification`'s `communitiesRepository.fetchById(notification.communityId)` now resolves. Verify against a real community whose `communityId` field differs from its document id — the lookup change is confirmed by inspection and by the `CommunitiesRepositoryTest` case in §12.4; the against-the-live-backend check needs a running app and is carried by the exit checklist (§13.5)
- [x] 4.4 Add `suspend fun fetchDocumentIdByCommunityId(communityId: String): String?` to `CommunitiesRepository` — the single `Communities.whereEqualTo("communityId", id)` lookup the `DeepLinkResolver` uses to translate a link's community id into the Korner detail's document id

## 5. Ranking screen — 2.6 + 2.7

- [x] 5.1 Create `RankingContract.kt` in `feature/ranking/list/` with `RankingState(rankings: AsyncValue<List<UserRanking>>)` — see `ranking-leaderboard` spec
- [x] 5.2 Create `RankingViewModel` (`@HiltViewModel`) — injects `GamificationRepository`, loads the **top 10** rows on init (`getRanking(10)`), and exposes the result as `AsyncValue`
- [x] 5.3 Create `RankingContent` in `feature/ranking/list/` — plain list, no separators, a header row reading `#` / `Name` / `Points` in three columns with the outer two at a fixed 75-dp width, and one row per entry
- [x] 5.4 Create the row: a leading 75-dp slot holding a 40 × 40 dp medal **or** the 1-based rank numeral at 16-pt bold in the secondary content colour; the user's name in bold; the points right-aligned in a 75-dp trailing slot — mirrors iOS `RankingScreen.swift:32-71`
- [x] 5.5 Port the iOS medal assets as Android vector drawables — `medal-gold`, `medal-silver`, `medal-bronze` from `kornerz/Kornerz/Resources/Assets.xcassets/Images/Medal/`. The iOS sources are SVGs with gradients; convert to `VectorDrawable` paths and verify the three read as distinct medals
- [x] 5.6 Create `RankingScreen` — the host (2.6) with the `Klouders` title, mirroring iOS `RankingViewController.configureNavigation` (`:27-32`)
- [x] 5.7 Render the three states: loading, error with a working retry, and the loaded list; the empty case renders the shared no-data view
- [x] 5.8 Expose each row as **one** accessibility node whose description states position, name, and points together; the medal SHALL NOT be a separate focusable node — see `ranking-leaderboard` spec
- [x] 5.9 Add `@Preview` composables for the header row, a podium row, a non-podium row, and the empty state

## 6. Shell wiring — the Klouders tab

- [x] 6.1 Replace `kloudersTabGraph` in `feature/shell/.../AppShell.kt` with the real ranking destination — `RankingScreen` from `feature/ranking`, replacing `tabComposableWithLogout(ShellTab.Klouders, onLogout)`
- [x] 6.2 Remove the P0 `EmptyTabScreen` "Log out" affordance from the Klouders tab — P1 shipped a real logout in P6, and `onLogout` is no longer needed by this tab
- [x] 6.3 Delete the now-unused `tabComposableWithLogout` helper and the `EmptyTabScreen` composable if nothing else references it — check `feature/shell` and `app` first
- [x] 6.4 Confirm the Klouders tab's bottom-bar visibility is unchanged — the tab is a normal tab, so the bar stays visible
- [x] 6.5 Consider porting the iOS `tab_klouders.svg` tab icon in place of the `Icons.Filled.Subscriptions` placeholder in `feature/shell/.../BottomBar.kt:38` — a design decision, but the asset exists at `kornerz/Kornerz/Resources/Assets.xcassets/Images/Home/tab_klouders.svg`

## 7. Deep-link manager + resolver

- [x] 7.1 Create `feature/shell/deeplink/DeepLinkManager.kt` — `@Singleton`, with a FIFO queue of pending links, `handle(deeplink)`, `notifyAppLaunch()`, and a way for the shell to take released links and for the interstitial to take its link — see `deep-link-routing` spec
- [x] 7.2 Implement the cold/warm split: before `notifyAppLaunch()` a link is queued; after it, a link is emitted immediately; a link queued before launch is released in queue order on launch
- [x] 7.3 Create `feature/shell/deeplink/DeepLinkTarget.kt` — a sealed interface with `LoopDetail(loopId)` and `KornerDetail(kornerId)`. These are **semantic** targets, not route strings, because both destinations live in a tab's nested `NavHost`
- [x] 7.4 Create `feature/shell/deeplink/DeepLinkResolver.kt` — maps a `DeepLink` to a `DeepLinkTarget` or to "not routable", per the routability matrix. `NewLoop` / `NewLoopComment` → `LoopDetail`; `VideoCall` → `KornerDetail`; `NewEvent` / `NewArticle` / `NewForum` / `NewComment` → not routable
- [x] 7.5 Implement the `VideoCall` translation: resolve the link's `communityId` field to the Korner's document id via `CommunitiesRepository.fetchDocumentIdByCommunityId`. A community id that does not resolve SHALL be reported as not routable
- [x] 7.6 Confirm `VideoCall` resolves to the **Korner detail**, not the video-call screen — iOS `handleCallLink` pushes a `KornerDetailController` (`DeeplinkViewController.swift:135-155`)

## 8. Deep-link interstitial (13.1) + routing

- [x] 8.1 Create `feature/shell/deeplink/DeepLinkResolvingScreen.kt` + `DeepLinkResolvingContent.kt` — a full-screen resolving state that takes the head of the manager's queue and asks the resolver
- [x] 8.2 When the link is routable: navigate to the resolved destination and remove the interstitial from the back stack — mirrors iOS `removeSelfFromNavigation()` (`DeeplinkViewController.swift:41-49`)
- [x] 8.3 When the link is not routable: show the alert "Failed to load deeplink. Will navigate back to home screen" and return the user to the Home tab — mirrors iOS `showAlert` (`:51-56`)
- [x] 8.4 Confirm the interstitial issues **no** Firestore read for the linked entity — the destination's own loading and error states own the failure surface
- [x] 8.5 Register `deeplink/resolving` as a top-level route on the shell's root `NavHost`, outside every tab's nested graph, with **no** navigation argument — see `android-app-shell` spec
- [x] 8.6 Extend the bottom-bar-hidden `Regex` in `AppShell.kt` with `deeplink/.*`
- [x] 8.7 In `AppShell`, call `DeepLinkManager.notifyAppLaunch()` once the `NavHost` is composed, in a `LaunchedEffect` declared **before** the premium-gate effect so the deep link lands before the paywall — see `deep-link-routing` spec
- [x] 8.8 In `AppShell`, collect the manager's released links and push `deeplink/resolving` once per link
- [x] 8.9 Execute the resolved target with the shell's existing cross-tab mechanism — `pendingLoopId` / `pendingKornerId` plus the tab switch, reusing `navigateToLoopDetail` and `navigateToKornerDetail`. Do **not** push `loops/{loopId}` or `kornerz/{kornerId}` on the root controller — they are nested destinations
- [x] 8.10 Confirm the paywall still unwinds onto the deep-link destination after a purchase (the P8 inclusive pop of `subscription/plans`) — the interstitial is pushed **before** the paywall, so the inclusive pop lands on the interstitial, which then resolves and navigates to the destination

## 9. App entry points — manifest, `MainActivity`, FCM

- [x] 9.1 Add the `kornerz` scheme `<intent-filter>` to `MainActivity` in `app/src/main/AndroidManifest.xml`, covering the `loop`, `forum`, and `community` hosts
- [x] 9.2 Set `android:launchMode="singleTop"` on `MainActivity` so a warm deep link arrives via `onNewIntent` instead of creating a second activity instance
- [x] 9.3 Create a `DeepLinkManagerEntryPoint` (`@EntryPoint`, `@InstallIn(SingletonComponent::class)`) in `app` and resolve the manager with `EntryPointAccessors.fromApplication` — the P8 `PremiumGateEntryPoint` precedent. Note the P8 lesson: the entry point **must** be `SingletonComponent`, not `ActivityComponent`, or the app crashes at launch with a `ClassCastException`
- [x] 9.4 In `MainActivity.onCreate`, read `intent.data` and hand any `kornerz://` URI to the manager — the **cold** path
- [x] 9.5 In `MainActivity.onNewIntent`, hand `intent.data` to the manager and call `setIntent(intent)` — the **warm** path
- [x] 9.6 Implement `DeepLinkPayloadParser` for the **push** dialect — `pushType` plus `communityId` / `eventId` / `articleId` / `forumId` / `loopId` — mirroring iOS `DeepLinksManager.handleDeepLink(info:)` (`:85-134`). Keep it distinct from the in-app document dialect
- [x] 9.7 In `KornerzFcmService`, build the tray notification's `PendingIntent` from the parsed push deep link's URI instead of `getLaunchIntentForPackage`. A payload with no recognisable `pushType` SHALL fall back to the launch intent — see `notifications-push-display` spec
- [x] 9.8 Confirm the notification still displays on the `kornerz_notifications` channel and that the data payload is still pushed into `NotificationRepository`'s cache

## 10. Notifications list routing

- [x] 10.1 In `NotificationsViewModel.openNotification`, change the `Open(deeplink)` event to carry the typed `DeepLink` instead of a `String` — see `notifications-list` spec
- [x] 10.2 Remove the `"Deeplink coming in Phase 10"` snackbar in `feature/shell/.../AppShell.kt` and route the `Open(deeplink)` event through the `DeepLinkResolver` instead
- [x] 10.3 Confirm `NewEvent` / `NewArticle` / `NewForum` / `NewComment` rows surface the shared "Failed to load deeplink. Will navigate back to home screen" alert and return the user to the Home tab — confirmed by inspection: every non-routable type resolves through the one `DeepLinkResolver`, and the `NotRoutable` branch shows the alert and navigates to the Home tab
- [x] 10.4 Confirm the `NewComment` row no longer shows the separate "Forum coming soon" snackbar — every non-routable link goes through the one resolver

## 11. Polish — shared components, standardisation, build

- [x] 11.1 Add `KornerzRetryView` to `core/ui/components/` — centred 12-pt message above a grey-tinted capsule "Retry again" button, mirroring iOS `Components/RetryView.swift`. Themed from `KornerzColors` / `KornerzTypography`, no literal hex — see `design-system` spec
- [x] 11.2 Add `KornerzNoDataView` to `core/ui/components/` — a 75 × 75 dp illustration above a body-size title with 50 dp of vertical padding, the illustration a parameter, mirroring iOS `Components/NoDataView.swift`
- [x] 11.3 Add `KornerzLoadingMoreView` to `core/ui/components/` — a centred full-width row with a progress indicator and the label "Loading more items" 16 dp beside it, mirroring iOS `Components/LoadingMoreView.swift`
- [x] 11.4 Replace the inline footer spinner in `NotificationsContent` (`feature/notifications/.../list/NotificationsScreen.kt:152-163`) with `KornerzLoadingMoreView`
- [x] 11.5 Sweep the other paginated list screens (Loops, Nooks, Kornerz, Home carousels) and move their pagination footers onto `KornerzLoadingMoreView` — **sweep result:** only the Loops list has a pagination footer (a "Loading…" text row at `LoopsContent.kt:239-252`); it now renders `KornerzLoadingMoreView`. The Nooks sections paginate in the ViewModel but render no footer, and the Kornerz / Home carousels are horizontal and do not paginate, so there is no footer to standardise there
- [x] 11.6 **Fallback only** (if task 1.5's ArchUnit artifact cannot be resolved): write a JUnit test in `app/src/test/` that scans the modules' Kotlin sources for forbidden imports — `core.*` importing `feature.*` or `app`, `feature.*` importing `app`, and `feature.*` importing another `feature.*` other than `feature.shell`. Same rules, no new dependency — **not required:** the ArchUnit artifact resolves (see §1.5), so §11.7 is the implemented path
- [x] 11.7 Write the ArchUnit test in `app/src/test/` — `ClassFileImporter().importPackages("com.kornerz")` with rules for the three boundary rules, allowing the `feature.shell` tab-graph seam. `app` is the only module whose unit-test runtime classpath holds every module's classes — **the test surfaced three pre-existing violations of `docs/architecture.md` §2.5, all of which are fixed here** (the spec's "the current tree passes" scenario requires it):
  - `feature.kornerz` → `feature.nooks` — the shared nook cards (`LiveKornerCard`, `ScheduledNookCard`, `nooksAccentColor`, `formatNookStartDate`, and their previews) moved to `core/ui/components/nooks/`, which is the shared-UI home the architecture names
  - `feature.notifications` → `feature.profile` — the notifications ViewModel now reads the session user from `core/storage`'s `SessionRepository` instead of the Profile feature's repository
  - `feature.profile` → `feature.community` — the admin-community **count** moved into `core/network`'s `CommunitiesRepository` (the data layer), with `feature/community`'s `AdminCommunitiesRepository` delegating to it so the Profile tab and the Community feature read the same `@Singleton`
- [x] 11.8 Add the R8 keep rules to `app/proguard-rules.pro` for the Vonage video SDK, the Firebase SDKs, kotlinx-serialization's generated serializers, and the `@Serializable` model classes
- [x] 11.9 Set `isMinifyEnabled = true` and `isShrinkResources = true` on the **release** build type in `app/build.gradle.kts`. Leave the debug build type unshrunk. Do **not** change the release `signingConfig`
- [x] 11.10 Accessibility pass over the P10-touched surfaces: a content description on every icon-only control, `null` on decorative icons, and no fixed-height text containers — the medal image and the no-data illustration are decorative (`contentDescription = null`), the bottom-bar icons carry their tab label, and the rank numeral's box is **width**-fixed but height-driven so an increased system font scale cannot clip it
- [x] 11.11 Verify the affected screens preview under the `en-rXA` pseudo-locale (per `phases.md` Principle 7 and `project-brief.md` §11.7) — the new components SHALL use `Alignment.CenterHorizontally` / `TextAlign.Center` / `padding(horizontal = …)` with no fixed start offsets — **verified by inspection:** the three new shared components centre their content and pad symmetrically, the leaderboard's flexible column is a `weight(1f)` rather than a fixed offset, and the rank numeral's box is centred. The on-device pseudo-locale pass is carried by §13.11 (it needs a signed-in session to reach the Klouders tab)

## 12. Testing

- [x] 12.1 `core:model` unit tests: `GamificationTest` (the `gameficationQtdPts` mapping is pinned against iOS's `CodingKeys`), `RankTest`, `UserRankingTest` (the `medal` property is a `Rank` defaulting to `NONE`), `DeepLinkTest` (every case round-trips through its URI; an unparseable URI yields `null`), `CommunityTest` (`communityId` defaults to `null`) — `UserRankingTest` lives in `RankTest` (the `medal` property *is* the `Rank` case), and `DeepLinkPayloadParserTest` was added alongside it for the push dialect
- [x] 12.2 `core:model` unit test: `UserNotificationDeeplinkTypeTest` — the seven iOS capitalised raw values parse, the lower-camel spellings still parse, and an unknown value yields `UNKNOWN`
- [x] 12.3 `core:model` unit test: `UserNotificationDeepLinkTest` — every case resolves with iOS's field requirements; `newComment` resolves to `NewComment` and **not** to a video call; a missing community or reference yields `null`
- [x] 12.4 `core:network` unit tests with `mockk` at the Firestore boundary: `GamificationRepositoryTest` (points read from `gameficationQtdPts`; names resolved from the `Users` batch; the `"User"` fallback; truncation to `count`; stable tie order; `Result.failure` on a query error; anonymous → `success(0)`), `CommunitiesRepositoryTest` (`fetchById` issues a `whereEqualTo("communityId", …)` query, not a `document(…)` lookup) — `core/network` had no test stack, so `junit` / `mockk` / `kotlinx-coroutines-test` were added to its `build.gradle.kts`
- [x] 12.5 `feature:ranking` unit tests: `RankingViewModelTest` with `Dispatchers.setMain(UnconfinedTestDispatcher())` — init requests the top 10, success and failure states land
- [x] 12.6 `feature:shell` unit tests: `DeepLinkManagerTest` (cold queueing, release order, warm emission, no overwrite of an in-flight link), `DeepLinkResolverTest` (the routability matrix, the community-id → document-id translation, an unresolvable community)
- [x] 12.7 `feature:notifications` unit test update: the `Open` event carries a typed `DeepLink` — `NotificationsViewModelTest` asserts the typed `Open` event, the loop / forum-comment events, and the shared "Community not found" alert
- [x] 12.8 `app` unit test: `ModuleBoundaryArchTest` — the three boundary rules pass against the P10 tree
- [x] 12.9 Compose UI tests: skeletons only, every case `@Ignore`d, in `feature/ranking/src/androidTest/` — `RankingContentTest` (a row is one semantics node; the medal is not separately focusable). **Not run**: no emulator is provisioned in CI and CI builds `assembleDevDebug` only. Verify it compiles via `:feature:ranking:assembleDevDebugAndroidTest` — the file is `RankingScreenTest.kt`; it compiles and the androidTest APK assembles ✓
- [x] 12.10 Integration test against the Firestore emulator: **deferred** — no emulator harness exists in this build (no `useEmulator`, no `FIRESTORE_EMULATOR_HOST`, no emulator Gradle config)

## 13. Verification + exit checklist

- [x] 13.1 Run `./gradlew :app:assembleDevDebug` — ✓ passes
- [x] 13.2 Run `./gradlew :app:assembleProdRelease` — the minified release APK builds — ✓ passes; `app/build/outputs/apk/prod/release/app-prod-release.apk` is produced
- [x] 13.3 Install and launch the **minified release** APK on an emulator and confirm no `FATAL EXCEPTION` in `adb logcat` — this is what verifies the R8 rules. If a class is stripped at runtime, add the missing keep rule; if the failure is a resource, revert `isShrinkResources` to `false` with a note and keep minification on — ✓ done on an API 35 emulator: the minified APK installs, launches, initialises Firebase and Crashlytics, and `adb logcat` shows **0** `FATAL EXCEPTION` lines. No keep rule had to be added beyond the ones §11.8 specifies
- [ ] 13.4 Run `./gradlew :app:installDevDebug` on an API 26 emulator and an API 35 emulator — **note**: P8 found that only the API 35 image is provisioned locally (`system-images/android-35`); if API 26 is still absent, record it as an unmet checklist item rather than silently skipping it — **partially met:** the dev debug APK installs and launches cleanly on the API 35 emulator (0 `FATAL EXCEPTION`). **API 26 is still absent** — only `system-images/android-35` is provisioned — so the API 26 pass remains an unmet checklist item
- [ ] 13.5 Launch the dev build and confirm the Klouders tab renders the leaderboard with real points and real names, and that the tab-header points chip shows a non-zero value — **unmet: needs a signed-in session.** The app has no demo account and the emulator has no stored session, so the build stops at the auth gate and the shell is never composed. The leaderboard's read path is covered by `GamificationRepositoryTest` instead
- [ ] 13.6 Launch the dev build and confirm a `kornerz://loop/{id}` intent reaches the loop detail — both cold (`adb shell am start -a android.intent.action.VIEW -d "kornerz://loop/…"`) and warm — **partially met:** the intent **delivery** is verified on-device for both paths — the cold path starts `MainActivity` with `dat=kornerz://loop/loop-abc123`, and the warm path reports "intent has been delivered to currently running top-most instance" (the `singleTop` + `onNewIntent` path). All five grammar hosts resolve. The **end-to-end hop into the loop detail** is unmet for the same reason as §13.5: the shell is behind the auth gate
- [ ] 13.7 Confirm an unroutable link shows the "Failed to load deeplink" alert and returns to Home — **unmet: needs a signed-in session** (the shell owns the alert). The routing branch itself is covered by `DeepLinkResolverTest`'s routability matrix
- [ ] 13.8 Confirm a tapped FCM notification carries its deep link — verify the `PendingIntent`'s data by inspecting the notification with `adb shell dumpsys notification` — **unmet: needs a real FCM push** (no Firebase test sender credentials in this environment). The `PendingIntent` construction is covered by `DeepLinkPayloadParserTest` on the payload side
- [x] 13.9 Run `./gradlew :app:lintDevDebug` — ✓ passes with **0 errors**; the 89 remaining warnings are the pre-existing ones (75 `GradleDependency`, 6 `AndroidGradlePluginVersion`, 5 `IconLauncherShape`, and 3 others). The `IntentFilterUniqueDataAttributes` warning the new deep-link filter raised was fixed by declaring the scheme and the hosts in separate `<data>` elements
- [x] 13.10 Run the full unit-test suite (`./gradlew test`) — green, with the P8 baseline of 504 tests still passing plus the new P10 tests — ✓ 644 test executions across 81 test classes, **0 failures, 0 errors** (the count exceeds the class count because the two flavoured modules — `feature:ranking` and `feature/subscription` — run their tests once per flavour)
- [ ] 13.11 Confirm the accessibility pass on the ranking screen and the new shared components — **on-device pass unmet** (needs a signed-in session to reach the Klouders tab). The code-level pass is done: the medal image and the no-data illustration are decorative (`contentDescription = null`), the bottom-bar icons carry their tab label, the rank numeral's box is width-fixed but height-driven, and the row is a single semantics node via `clearAndSetSemantics`
- [ ] 13.12 Capture one screenshot per in-scope screen (2.6/2.7 ranking, 13.1 interstitial, the three shared components in use) and attach to the PR — **manual** — **partially met:** the launch screenshots are captured (`/tmp/p10-shots/`), but the in-scope screens are behind the auth gate
- [ ] 13.13 Record a ≤ 5-min Loom walkthrough: open the Klouders tab, tap a notification, follow a deep link, show the empty and error states — **manual**
- [ ] 13.14 Create the `phase-10-end` git tag and push — **manual**: a tag and a push are not performed without an explicit request
- [ ] 13.15 Share the demo-ready APK with all stakeholders for the feature-by-feature parity demo against iOS (`docs/phases.md:400`) — **manual**

## 14. Deferred (documented, not implemented)

- [x] 14.1 **No** Event Detail, Post Detail, or Forum Detail screens. Their deep-link types get the "not routable" path; the screens stay excluded per `docs/screen-inventory.md:229-237` — confirmed: `DeepLinkResolver` reports `NewEvent` / `NewArticle` / `NewForum` / `NewComment` as `NotRoutable`
- [x] 14.2 **No** `manageSubscriptions` hand-off to the Play Console (deferred from P8) and no client-side subscription cancel button
- [x] 14.3 **No** release-signing change — `docs/phases.md:407` open question 3. The release build type keeps the debug keystore; the minified APK is install-and-launch verified only — confirmed: the release build type's `signingConfig` is unchanged
- [x] 14.4 **No** pt-BR localisation of the new screens and components — follow-up per `project-brief.md` §11.7. The iOS values used here (`klouders`, `retry_again`, `loading_more_items`) are recorded in the `app-polish` spec
- [x] 14.5 **No** crash-free-sessions dashboard. Crashlytics is already wired; the ≥ 99.5% SLO in `docs/phases.md:321` is a measurement target, not a code deliverable
- [x] 14.6 **No** performance work beyond the cold-start budget and R8 minification — no baseline profile, no ART warmup, no image-format migration
- [x] 14.7 **No** change to `Community.id`'s or `Community.code`'s meaning. The P10 change adds `Community.communityId` and fixes the `fetchById` **lookup**; the model's existing id semantics are left alone so P3/P4 behaviour is not disturbed — confirmed: `fetchById` / `fetchByName` / `fetchFollowingCommunities` still put the `communityId` field into `id` and `code` exactly as before
- [x] 14.8 **No** leaderboard pagination beyond the top 10 — iOS caps at 10 (`RankingViewModel.swift:56`) and Android matches — confirmed: `RankingViewModel.TOP_ROWS` is 10
