## Context

P10 is the closing phase of the Android port. P0–P9 shipped the app's seven feature areas; P10 adds the last two screens (the Klouders leaderboard, the deep-link interstitial) and a cross-cutting polish pass, then closes the phased-delivery plan.

The proposal lists six defects and four open stubs found while auditing the Android tree against iOS. The four that shape this design are architectural rather than cosmetic:

- **The leaderboard read path is wrong in two independent ways.** `GamificationRepository` reads points from `gameficationPoints`, a field that does not exist (iOS maps `points` → `gameficationQtdPts`), and it never resolves user names, because iOS resolves them in a **second** query (`Users.whereField("codeUser", in: userIds)` → `nickName`) that the Android port skipped. The fix is not a one-line rename: the read path needs a batched second query, and the leaderboard rows need a name that survives it.
- **Deep links have two payload dialects.** The Firestore notification *document* carries `notificationType` / `notificationReferenceId` / `notificationCommunityId`; the FCM *push* payload carries `pushType` / `communityId` / `eventId` / `articleId` / `forumId` / `loopId`. iOS keeps them distinct (`Models/UserNotification.swift` vs `DeepLinks/DeeplinkManager.swift:85-134`), and Android must too, or the tray-tap path and the in-app-list path will disagree about what a link means.
- **The community identifier is ambiguous.** iOS `Community` has **two** ids: `id` (the `@DocumentID` — the Firestore document id) and `communityId` (a separate public field). iOS resolves a community *by the field* (`CommunitiesRepository.swift:241`, `APICommunity.swift:475`) and then overwrites `id` with the document id (`APICommunity.swift:483`). Android's `CommunitiesRepository.fetchById` resolves by **document id** (`collection.document(id)`) — the wrong key for anything driven by a notification or a push payload. Both the deep-link work and the existing P6 notification flow depend on this being fixed.
- **Three of iOS's seven deep-link targets are excluded from the Android port.** Event Detail, Post Detail, and Forum Detail were dropped as dead code by stakeholder review on 2026-10-07 (`docs/screen-inventory.md:229-237`). A deep-link router must therefore have an explicit answer for a link it cannot honour, rather than silently doing nothing.

Constraints carried from earlier phases: the module boundary rule (`docs/architecture.md` §2.5 — `feature/*` imported by `app` only, in practice via `feature/shell`'s tab-graph seam), the UDF contract (`AsyncValue<T>` state, `Channel<NavEvent>` for one-shot effects), the wrapper-only SDK boundary (there is no Firestore fake, no Robolectric, no Hilt test harness), English-only localisation, and the real `kornerz-72fba` backend with no mock servers.

## Goals / Non-Goals

**Goals:**

- Ship the Klouders leaderboard (screens 2.6 + 2.7) with real data, correct points, real user names, and iOS-parity medals and row layout.
- Ship a deep-link subsystem that resolves a `kornerz://` URI to a live destination, handles cold start and warm start, routes a tapped FCM notification, and gives an explicit answer for a link it cannot honour.
- Standardise the empty / error / no-data / loading-more surfaces onto shared components that mirror their iOS counterparts.
- Make the release build minified and survivable, with verified R8 keep rules for Vonage and Firebase.
- Enforce the module-boundary rule at compile time with the Gradle dependency graph `docs/architecture.md` already mandates.
- Leave the tree in a state where the P10 exit checklist (`docs/phases.md:371-383`) can be executed verbatim.

**Non-Goals:**

- Resurrecting Event Detail, Post Detail, Forum Detail, or the Article/Event/Forum creation wizards. They stay excluded; their deep-link types get an explicit "not available in this build" path.
- Any change to the `Gamefication` collection's shape or to the backend. The leaderboard is read-only.
- A `manageSubscriptions` hand-off to the Play Console (deferred from P8) and a client-side subscription cancel button.
- Release signing configuration (`docs/phases.md:407` open question 3). The release build type keeps the debug keystore.
- A crash-free-sessions dashboard. Crashlytics is already wired; the ≥ 99.5% SLO in `docs/phases.md:321` is a measurement target, not a code deliverable.
- pt-BR localisation, per `project-brief.md` §11.7.
- Performance work beyond the cold-start budget and R8 minification — no baseline profile, no ART warmup, no image-format migration.
- A general rewrite of `Community`'s id modelling. The P10 change is the narrow lookup fix described in Decision 6; `Community.id`'s `@SerialName("id")` stays as it is so P3/P4 behaviour is not disturbed.

## Decisions

### Decision 1 — The leaderboard ships in a new `feature/ranking` module

**Choice.** A new Gradle module `feature/ranking` (`namespace = "com.kornerz.feature.ranking"`), mirroring `feature/profile/build.gradle.kts`, hosting `list/` (2.6 host + 2.7 view), `components/` (the medal slot and the header row), and `navigation/` (`RankingRoute`).

**Alternatives.**
- *Put the screen in `feature/shell`.* `feature/shell` already owns the tab graph and already imports every other feature module. This avoids a new module. Rejected: `feature/shell` is the routing seam, not a screen container, and `project-brief.md:158` already reserves `feature/ranking/` for the Klouders tab. A separate module keeps the screen's tests and its Hilt graph out of the shell's compile unit.
- *Put the screen in `feature/home`.* The leaderboard is not a Home section. Rejected.

**Consequence.** `feature/shell` adds `implementation(project(":feature:ranking"))`, joining the existing tab-graph seam. `feature/ranking` must not import `feature/shell`. `app` adds the module to `settings.gradle.kts` and `app/build.gradle.kts`. This is the one place the "imported by `app` only" rule is relaxed in practice — the same relaxation every other tab already has — and Decision 12's rule set encodes it explicitly rather than pretending it away.

### Decision 2 — The leaderboard's read path becomes two queries, and names are resolved in the second

**Choice.** `GamificationRepository.getRanking(count)` reads the `Gamefication` collection exactly as iOS does (range on `gameficationDateHora`, `startAfter` pagination), aggregates points **per user id** using `gameficationQtdPts`, truncates to `count`, then issues **one** batched `Users.whereEqualTo("codeUser", in: userIds)` query and assigns each row's `nickname` as its `userName`. Rows whose user id no longer resolves keep the literal fallback `"User"` — the same fallback iOS uses when it creates the row (`GamificationService.swift:124`).

**Alternatives.**
- *One query with a join.* Firestore has no joins; a client-side join is exactly what iOS does. Rejected as not-an-option.
- *Resolve names lazily per row.* N+1 reads. Rejected.
- *Read the nickname from the `Gamefication` document.* The `Gamefication` document has no nickname field (iOS `Models/Gamification.swift` — `id`, `action`, `date`, `points`, `userId`, `referenceId`). Rejected.

**Consequence.** `getUserPoints()` — the tab header's points chip, which has read `0` on every tab since P2 — is fixed by the same field rename and needs no second query, because it already filters by the signed-in user's own `codeUser`.

### Decision 3 — The leaderboard's ties are broken deterministically; iOS's are not

**Choice.** Android orders rows by points descending with `sortedByDescending`, which is a **stable** sort, so rows with equal points keep their first-seen order (the Firestore document iteration order). Medals are assigned by final position: gold, silver, bronze for the first three rows, none after.

**Alternatives.**
- *Reproduce iOS exactly.* iOS sorts with `sorted(by: { $0.points > $1.points })`, which is not guaranteed stable, and its tie order is therefore arbitrary and can differ between the two platforms on the same data. Reproducing that would make the parity demo show a different order on Android and iOS for tied rows.

**Consequence.** A deliberate, documented divergence: for tied rows the two platforms may order differently, and Android's order is at least reproducible. This is recorded in the `ranking-leaderboard` spec so a future pt-BR or parity pass does not "fix" it back.

### Decision 4 — The deep link is a typed model in `core/model`; the URI is its transport form

**Choice.** A `DeepLink` sealed interface in `core/model` mirroring iOS `DeepLinks/DeeplinkManager.swift:13-54` case-for-case, with `toUri()` and a companion `parse(uri)`. The `kornerz://` grammar is:

```
kornerz://loop/{loopId}                          → NewLoop / NewLoopComment
kornerz://forum/{forumId}                        → NewComment
kornerz://community/{communityId}/call           → VideoCall
kornerz://community/{communityId}/event/{id}     → NewEvent
kornerz://community/{communityId}/article/{id}    → NewArticle
kornerz://community/{communityId}/forum/{id}      → NewForum
```

`UserNotification.deeplink(with:)` changes its return type from `String?` to `DeepLink?`.

**Alternatives.**
- *Keep the URI as the only representation.* The existing `deeplink(with:)` already returns a `String?`, so this is the zero-change option. Rejected: a stringly-typed link cannot be exhaustively matched, the existing `user-notification-domain` spec already describes a typed `DeepLink` with `EventRef` / `LoopRef` / … cases, and the P10 change is the moment to make the code match that contract.
- *Use Android App Links (`https://kornerz.io/...`).* Rejected: App Links require `assetlinks.json` served from a verified domain, which is an infrastructure dependency outside the repository and outside the Android port's scope. A custom scheme is what the existing code already mints.
- *Keep the iOS URI shapes verbatim.* iOS has no URI form at all — it passes the typed enum around in memory. Android needs a URI only because the transport between `MainActivity` and the shell is an `Intent`. Rejected: the shapes above are Android's own and are the minimum that round-trips.

**Consequence.** `kornerz://loop/{loopId}` is lossy between `NewLoop` and `NewLoopComment` — the existing Android implementation already collapses them, and both resolve to the same destination (the loop detail), so the loss is harmless. This is stated in the spec.

### Decision 5 — `DeepLinkManager` lives in `feature/shell` and holds links in a FIFO queue

**Choice.** A `@Singleton` `DeepLinkManager` in `feature/shell/deeplink/`, with iOS `DeepLinksManager`'s three responsibilities: `handle(deeplink)` accepts a link, `notifyAppLaunch()` marks the app ready, and the held link is routed once the app is ready. Links are held in a FIFO queue. `MainActivity` reaches it through a Hilt `@EntryPoint` (the P8 `PremiumGateEntryPoint` precedent), and the shell composes `notifyAppLaunch()` in a `LaunchedEffect`.

**Alternatives.**
- *Put it in `core/network`.* It is a routing concern with no network access. Rejected: `core/network` holds repositories and Firebase wrappers.
- *Put it in `app`.* Then `feature/shell` could not reach it without a callback seam on every tab. Rejected: `feature/shell` is where the `NavHost` lives, so the manager must be reachable from there.
- *Pass the link down as a Compose parameter.* The link can arrive before the shell exists (cold start) and after it exists (warm), so it needs a process-scoped holder. Rejected.
- *Put the pending link in a route argument.* A route argument must be encoded, and the deep-link ids are Firestore ids whose character set is not guaranteed. Rejected: the manager holds the link in memory, exactly as iOS's `CurrentValueSubject` singleton does.

**Consequence.** A cold-start deep link for a signed-out user is held until the shell is composed — i.e. until after login. That is iOS parity: `DeepLinksManager.notifyAppLaunch()` is called from `AppTabController.viewDidLoad` (`AppTabController.swift:24`), which only exists post-login.

### Decision 6 — The community lookup is fixed to resolve by the `communityId` field, and the resolver normalises it

**Choice.** Two changes, kept separate:

1. `CommunitiesRepository.fetchById(id)` changes its **lookup** from `collection.document(id)` to `whereEqualTo("communityId", id)`, matching iOS (`CommunitiesRepository.swift:241`, `APICommunity.swift:475`). It keeps populating `id` with the `communityId` field, so `Community.id`'s meaning is unchanged and P3/P4 are untouched. A `communityId` field is added to the `Community` model so the two ids are nameable, and `UserNotification.deeplink(with:)` uses it.
2. The `DeepLinkResolver` resolves the `communityId` **field** to the `Communities` **document id** before navigating, because the Korner detail route is addressed by document id (`KornerFirestoreApi.fetchDetail` does `collection("Communities").document(kornerId)`).

**Alternatives.**
- *Change `Community.id` to be the document id everywhere.* Correct in the abstract, but `Community.id` currently holds the `communityId` field in the paths that matter and is read by the loops/home mappers. Changing its meaning silently would break the home and loops community-id lookups. Rejected.
- *Make the Korner detail route accept the `communityId` field.* That would mean a second Firestore query inside `KornerFirestoreApi.fetchDetail` and a change to the route contract that P3 already shipped. Rejected.
- *Have the FCM service resolve the id before building the URI.* The FCM service runs in the background with no guarantee the app is alive; a network call there is fragile and the result would be lost if the app is killed. Rejected.

**Consequence.** The **existing** P6 notification flow improves as a side effect: `NotificationsViewModel.openNotification` already calls `communitiesRepository.fetchById(notification.communityId)`, which today misses for every community whose `communityId` field differs from its document id. That is the "Community not found" alert users see today.

### Decision 7 — The interstitial is a route with no argument; the manager hands it the link

**Choice.** The deep-link interstitial (13.1) is a top-level `deeplink/resolving` route on the shell's root `NavHost`, registered in `feature/shell`. It takes **no route argument**: the shell takes the link off the manager's queue and the interstitial takes it back through the manager. It resolves the link and then navigates to the destination, popping itself off the back stack.

**Alternatives.**
- *Encode the link into the route.* Encoding a `DeepLink` into a path segment means either a custom codec (a second serialisation format to maintain and to test) or URL encoding with its own escaping rules. Rejected for a transient screen.
- *Resolve the link in the shell and skip the interstitial.* Then screen 13.1 does not ship, and the phase's screen inventory is short one live screen. Rejected.
- *Pre-fetch the entity in the interstitial, like iOS.* iOS's `DeeplinkViewController` fetches the Loop and its publisher and community before building the `LoopDetailController`, because iOS's detail controller takes a fully-populated `LoopInfo` object rather than an id. Android's detail screens take an **id in the route** and fetch their own data — the destination's own loading and error states already cover the failure case. Duplicating the fetch in the interstitial would create two owners of the same load. Rejected.

**Consequence.** The interstitial's job is narrowed to: show the resolving state, ask the resolver whether the link is routable, navigate or fail. It does not own data loading. On process death while the interstitial is on screen the link is gone (it lived in memory) and the interstitial shows the failure path — honest, and consistent with iOS's in-memory manager.

### Decision 8 — The routability matrix is explicit and lives in one place

**Choice.** `DeepLinkResolver` maps a `DeepLink` to a `DeepLinkTarget` or to "not routable":

| `DeepLink` case | Target | Routable on Android? |
|---|---|---|
| `NewLoop` | `LoopDetail(loopId)` | yes — `loops/{loopId}` |
| `NewLoopComment` | `LoopDetail(loopId)` | yes — `loops/{loopId}` |
| `VideoCall` | `KornerDetail(kornerId)` | yes — `kornerz/{kornerId}` |
| `NewEvent` | — | no — Event Detail is excluded |
| `NewArticle` | — | no — Post Detail is excluded |
| `NewForum` | — | no — Forum Detail is excluded |
| `NewComment` | — | no — Forum Detail is excluded |

A link that is not routable shows the iOS-parity alert text "Failed to load deeplink. Will navigate back to home screen" (`DeeplinkViewController.showAlert`, `:51-56`) and returns the user to the Home tab.

**Alternatives.**
- *Add the missing screens so every link routes.* Rejected: `docs/screen-inventory.md:229-237` records the stakeholder decision that those 9 screens are out of the port.
- *Ignore an unroutable link silently.* Rejected: the user tapped a notification and nothing would happen.

**Consequence.** `DeepLinkTarget` is a shell-level type (`LoopDetail` / `KornerDetail`), not a raw route string. Both targets are destinations of a **nested** `NavHost` (the Loops tab and the Kornerz tab), so the shell cannot push them on the root controller directly — it reuses the existing cross-tab mechanism (`pendingLoopId` / `pendingKornerId` plus a tab switch), the same one `navigateToLoopDetail` and `navigateToKornerDetail` already use. The resolver therefore produces a *semantic* target and the shell owns the navigation.

### Decision 9 — The two payload dialects are parsed by two separate functions

**Choice.** Two parsers, one per dialect, both producing a `DeepLink`:

- `UserNotification.deeplink(with:)` — the **document** dialect (`notificationType` + `notificationReferenceId` + the community id). Produces a `DeepLink` directly, with no URI in between.
- `DeepLinkPayloadParser` — the **push** dialect (`pushType` + `communityId` + `eventId` / `articleId` / `forumId` / `loopId`), mirroring iOS `DeepLinksManager.handleDeepLink(info:)` (`:85-134`). It produces a `DeepLink`, and the FCM service turns that into the `kornerz://` URI carried by the `PendingIntent`.

The `kornerz://` URI is the only thing that crosses the process boundary (`MainActivity`'s `Intent`); inside the app, links are the typed model.

**Alternatives.**
- *One parser with optional fields.* A single parser with nullable id fields cannot express "this case requires two ids" and would let a malformed link through to the resolver. Rejected.
- *Have the FCM service read the `Gamefication`-style notification document.* The push payload does not carry the document id. Rejected.

**Consequence.** The `pushType` raw values are iOS's capitalised `NewEvent` / `NewArticle` / `NewForum` / `VideoCall` / `NewComment` / `NewLoop` / `LoopComment` — the same vocabulary as `DeeplinkType`. The document dialect's `notificationType` values are the same vocabulary, which is why `UserNotificationDeeplinkType.fromRaw` must be case-insensitive.

### Decision 10 — The deep link is delivered before the premium gate

**Choice.** The shell releases the pending deep link in a `LaunchedEffect` declared **before** the premium-gate effect, so the deep-link destination is on the stack first and the paywall is pushed on top of it.

**Alternatives.**
- *The gate first.* Then the paywall covers the destination and, because P8 made the paywall non-dismissible, the user must purchase before reaching the link. Worse for the user and further from iOS.
- *Defer the link until the gate is satisfied.* Adds a second piece of pending state for a case (free user + deep link) that is rare.

**Consequence.** iOS's ordering matches: `AppTabController.viewDidLoad` calls `DeepLinksManager.shared.notifyAppLaunch()` (`:24`) after `addObservers()` registers the paywall sink, so the deep link is pushed and the paywall is presented modally over it. On Android, the P8 inclusive-pop of `subscription/plans` lands the user on the deep-link destination after purchase.

### Decision 11 — The polish pass adds three named components and standardises onto them

**Choice.** `core/ui/components/` gains `KornerzRetryView`, `KornerzNoDataView`, and `KornerzLoadingMoreView`, each a literal structural mirror of its iOS counterpart (`Components/RetryView.swift`, `Components/NoDataView.swift`, `Components/LoadingMoreView.swift`). The existing `KornerzEmptyState`, `KornerzErrorState`, and `KornerzLoadingView` are **not** removed — they are the generic components the earlier phases already use — but the list screens standardise their pagination footer onto `KornerzLoadingMoreView` and their empty/error surfaces onto the shared set.

**Alternatives.**
- *Rename `KornerzEmptyState` to `KornerzNoDataView`.* One component, one name. Rejected: `KornerzEmptyState` is referenced by six screens across P2–P6 and is already covered by existing specs; renaming it would touch unrelated specs for no behavioural gain.
- *Inline the components per screen.* Rejected: that is the duplication the phase is meant to remove.

**Consequence.** `KornerzErrorState` currently renders an `Outlined` `KornerzButton` labelled "Retry". iOS's `RetryView` renders a grey-tinted capsule labelled "Retry again". The new `KornerzRetryView` carries the iOS shape; `KornerzErrorState` is left in place for the screens that already use it, and the list screens move to `KornerzRetryView`.

### Decision 12 — The module-boundary rule is enforced by the Gradle dependency graph

**Choice.** `docs/architecture.md` §2.5 requires the boundary rule to hold. The project carries no automated test suite, so the rule is enforced where it is actually observable: at compile time. `core/*` modules declare their dependencies with `implementation` / `api` and never reference a `feature/*` module, so a `core` → `feature` import does not compile. The same holds for `feature.*` → `app` and for feature → feature edges outside `feature.shell`'s tab-graph seam. The rules encode the rule as it is actually practised:

- `core.*` never depends on `feature.*` or `app`;
- `feature.*` never depends on `app`;
- no feature module depends on another feature module **except** `feature.shell`, which owns the tab-graph seam.

**Alternatives.**
- *Enforce the rule with ArchUnit.* Rejected: the project does not maintain an automated test suite, so an ArchUnit test would be a test nobody runs and nobody maintains.
- *Put the check in a dedicated module.* Adds a module to `settings.gradle.kts` for one check. Rejected.

**Consequence.** No new third-party dependency, and no test source set. The rule is verified by `./gradlew :app:assembleDevDebug` succeeding — a boundary violation is a compile error, not a failing assertion.

### Decision 13 — R8 is enabled on the release build type, and the keep rules are written for what is actually used

**Choice.** `app/build.gradle.kts` sets `isMinifyEnabled = true` and `isShrinkResources = true` for the `release` build type. `app/proguard-rules.pro` gains the rules the shipped dependency set needs: the Vonage video SDK (whose classes are resolved reflectively and instantiated from native code), the Firebase SDKs (which look up classes by name), kotlinx-serialization's generated serializers, and the `@Serializable` model classes.

**Alternatives.**
- *Leave minification off and only write the rules.* The phase says "ProGuard / R8 rules **verified**". Rules that are never exercised are not verified. Rejected.
- *Enable minification on the `debug` build type.* Debug builds are the phase's install-and-launch artefact; minifying them slows iteration and makes stack traces unreadable. Rejected — minification is a release concern.

**Consequence.** The release build type still signs with the debug keystore (`docs/phases.md:407` open question 3), so the minified APK is installable and launchable but not uploadable to Play. The verification step is `./gradlew :app:assembleProdRelease` plus a clean install and launch on the emulator — if R8 strips something at runtime that the build cannot catch, the launch check is what surfaces it.

### Decision 14 — The accessibility pass is scoped to the components P10 touches

**Choice.** The accessibility pass covers: a content description on every icon-only control in the new and touched surfaces; the leaderboard row exposed as **one** semantics node whose description reads the position, name, and points together (so TalkBack does not read the medal, the numeral, the name, and the points as four disconnected fragments); and no fixed-height text containers, so the system font scale can grow the type without clipping. The pass does not attempt a screen-reader audit of all 77 screens.

**Alternatives.**
- *Audit and fix all 77 screens.* Not a phase-sized piece of work and not what the phase's demo claim rests on. Rejected.
- *Add a lint baseline.* Android Lint's accessibility checks are noisy enough that a baseline would hide regressions. Rejected.

**Consequence.** The pass is verifiable by inspection of the composable semantics, not by a TalkBack session. That limitation is stated in the tasks.

## Risks / Trade-offs

- **The points field rename could break a screen that has been silently showing `0`.** The tab header's points chip (`core/ui/.../header/TabHeader.kt:123`) reads `getUserPoints()`, which has always returned `0` on Android. After the fix it will show real numbers, which changes the Home / Kornerz / Nooks / Loops headers. → *Mitigation:* the field name is taken verbatim from iOS `Models/Gamification.swift:23` and the mapping is checked against the iOS `CodingKeys` by inspection, so the rename cannot be "corrected" back into a guess. The visual change is a fix, and the P10 demo should show it.

- **A `Gamefication` document written by a different backend version might use `gameficationPoints`.** Nothing in the repository proves the server's field name beyond iOS's `CodingKeys`. → *Mitigation:* the read path accepts `gameficationQtdPts` as the authoritative field and falls back to `gameficationPoints` when the authoritative field is absent on a document, logging neither. Both spellings are read; only the iOS one is written. This costs one extra `getLong` per document and removes the single-point-of-failure that produced defect 2 in the first place.

- **The `communityId`-field vs document-id split could silently break the home and loops mappers.** `Community.id` currently holds the `communityId` field in the paths P10 touches, and the loops mapper looks up `communitiesById[loop.communityId]`. → *Mitigation:* Decision 6 changes the **lookup**, not the field's meaning; `Community.id` keeps its current value everywhere. The new `communityId` field is additive and defaulted to `null`, so no existing constructor call site changes.

- **`Communities.whereEqualTo("communityId", …)` needs no composite index, but the leaderboard's `Gamefication` range query might.** The range is on a single field (`gameficationDateHora`) and the `Users` lookup is an equality on `codeUser` — both single-field, so neither needs a composite index. The `Users` lookup is an `in` query over up to 10 ids, which Firestore evaluates with an automatic index. → *Mitigation:* the leaderboard's first live run is verified against the real backend; if Firestore reports a missing index, the error surfaces in the `Result.failure` path and the retry view, not as a crash.

- **R8 may strip a class that is only reachable reflectively.** The Vonage SDK and the Firebase SDKs are the known cases; the `kotlinx-serialization` generated `Companion.serializer()` calls are another. → *Mitigation:* Decision 13's keep rules cover all three categories, and the verification step is a real install-and-launch of the minified APK, not just a successful assemble.

- **A boundary violation between `core/*` and `feature/*` could reach the merge.** → *Mitigation:* Decision 12's rule is enforced by the Gradle dependency graph — a `core` → `feature` import does not compile — and reviewed in the diff.

- **Enabling `isShrinkResources` can strip resources that are only referenced from XML or from the manifest.** → *Mitigation:* the launch check on the minified APK is the gate; if a resource is missing at runtime the release APK fails to open and the task is reverted to `isShrinkResources = false` with a note, keeping minification on.

- **A deep link arriving while the paywall is open is an ordering edge case.** → *Mitigation:* Decision 10 fixes the order; the manager's FIFO queue means a second link waits rather than overwriting the first, and the interstitial processes one link at a time.

- **The `kornerz://` scheme is registered without `android:autoVerify`, so any app can claim it.** → *Mitigation:* the scheme is a *routing* convenience, not an authentication surface. The link only ever navigates to a screen the signed-in user could reach directly, and every destination re-reads its own data under the session. No credential or authorisation decision depends on the URI.

- **Two of iOS's polish views are visual approximations on Android.** iOS's `NoDataView` uses a 75-pt `tray.fill` SF Symbol and a Satoshi-14 title; the Android equivalent uses a Material icon and the Kornerz typography scale. → *Mitigation:* the component mirrors iOS's **structure** (illustration above title, vertical padding), not its exact asset, and the icon is a parameter so a designer-supplied illustration can be dropped in without touching the component.

## Migration Plan

This change ships a client application; there is no server-side migration and no data migration.

1. **Schema / data:** none. The `Gamefication` and `Users` collections are read-only from Android, exactly as iOS reads them. The `Gamefication` field-name fix is a *read* correction — it makes Android read the field the server already writes.
2. **Backend:** none. The shared `kornerz-72fba` project is untouched.
3. **Client rollout:** the `kornerz://` scheme is added to `AndroidManifest.xml` in the same APK that ships the resolver, so there is no window in which an external URI arrives with no handler. `MainActivity`'s `launchMode` moves to `singleTop` in the same change, so the warm-intent path is live from the first release that supports the scheme.
4. **Rollback:** the phase is a single APK. Reverting the branch removes the scheme registration, the resolver, and the leaderboard together; no persisted state is added, so nothing needs to be unwound on device. The one persisted-state-adjacent change is `Community.communityId` being added to the model — it is a new nullable field with a `null` default and is not written to any store, so an older APK reading a `SessionRecord` that contains it simply ignores the unknown key (`SessionSerializer` uses kotlinx-serialization with the project's configured `ignoreUnknownKeys`).
5. **Verification:** the phase's exit checklist (`docs/phases.md:371-383`) is the acceptance gate — `./gradlew :app:assembleDevDebug`, install on API 26 and API 35 emulators, a clean launch with no `FATAL EXCEPTION` in `adb logcat`, `./gradlew :app:lintDevDebug`, one screenshot per in-scope screen, and a ≤ 5-minute walkthrough.

## Open Questions

- **Does the `Gamefication` collection's `gameficationQtdPts` field hold a `Long` or an `Int` on every document?** The read uses `doc.getLong(...)` and coerces, so both work. Not blocking.
- **Should the leaderboard paginate beyond the top 10?** iOS caps at 10 (`RankingViewModel.swift:56`) and Android matches. A "show more" affordance would be an iOS divergence and is deferred.
- **What is the `Klouders` tab icon?** The bottom bar currently uses `Icons.Filled.Subscriptions` as a placeholder (`BottomBar.kt:38`). iOS ships `tab_klouders.svg`. Porting the iOS asset is a one-line change and is in scope for the polish pass; the icon's final form is a design decision.
- **Should the deep-link interstitial be skippable?** iOS's is not (it removes itself once the destination is pushed). Android matches.
- **Where does the release upload key live?** Unresolved `docs/phases.md:407` open question 3. P10 does not change the signing configuration; the minified release APK is install-and-launch-verified only.
