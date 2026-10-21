## Context

Phases P0–P6 shipped a populated Home, Kornerz, Nooks, Loops, Profile, and Notifications tab on the shared Firebase backend (`kornerz-72fba`), plus the full signup / login / session / force-update / FCM / privacy-policy / avatar-upload / edit-basic-info plumbing. The Profile tab already conditionally surfaces a "My Communities" menu row when `ownedCommunityCount > 0` (per P6's `user-profile` capability), but the destination is a `toast("Coming in Phase 7")` placeholder — neither the create-community wizard nor the admin-owned-communities list has an Android surface yet, and there is no Android `Community` / `CommunityAdmin` model, no `CommunitiesRepository.create(...)`, no `AdminCommunitiesRepository.fetchUserOwnedCommunities(...)`, and no Firebase Storage v4 signed-URL upload helper that the wizard needs for the cover photo. Phase 7 closes that gap by porting the iOS Create Community & Admin vertical onto Compose.

iOS (`kornerz/Kornerz/HomeVC/CreateCommunity/`, `kornerz/Kornerz/HomeVC/CommunityDetailAsAdmin/AdminCommunitiesList/`, `kornerz/Kornerz/Backend/Repositories/CommunitiesRepository.swift`) is the source of truth for behavior, fields, sort orders, compression qualities, and interactions. The architectural baseline from P6 is unchanged: MVVM + Repository + UDF, `AsyncValue<T>` per section, `Channel<NavEvent>` for navigation, Hilt scopes (`@HiltViewModel` for VMs, `@Singleton` for repositories), `SavedStateHandle` for nav-arg VMs, `core/model` for `@Serializable` data, Coil for images, and the standard `feature/*` Gradle scaffolding.

The new additions are one feature module (`feature/community`) hosting two sub-packages (`create/`, `admin/`), four new view models, two new repositories, one new storage API, one new model with two data classes plus one enum, six new composables, and the shell wiring for two new top-level routes. **Zero new third-party dependencies** are introduced; the v4 signed-URL upload helper added in P6 is reused. The community-detail-as-admin iOS chain is documented as removed-dead-code in `screen-inventory.md` §"What is NOT in the Android port"; Android mirrors the no-firmware surface by routing row taps to a `toast("Coming soon")` placeholder until a follow-up phase decides what's needed.

## Goals / Non-Goals

**Goals:**

- Port the Create Community bottom-nav Profile menu row (always visible) which presents the wizard orchestrator (9.1).
- Port the 3-step wizard — Name (9.2), Description (9.3), Theme picker (9.4) — exactly mirroring the iOS chrome and per-step validation (Name maxLength 80, Description maxLength 500, theme picker with six themes).
- Implement the `CommunitiesRepository.create(draft)` flow that generates a 6-char alphanumeric community code, uploads the cover JPEG + thumbnail via the existing P6 v4 signed-URL helper, and writes `Communities/{code}` via `setData(community)` (overwrite — iOS does not use merge for new communities).
- Implement the admin-owned-communities list (10.1 + 10.2 + 10.3) — `LazyColumn` of `CommunityItemRow`s sorted by `createdAtTimestamp DESC`, with shimmer / error+retry / empty / loaded render states, pull-to-refresh, per-row member + nook count pills.
- Add `Community` + `CommunityAdmin` + `CommunityTheme` enum to `core/model` mirroring iOS `Models/Community.swift` field-for-field, including the v4-first / IPFS-fallback `coverImageURL` chain.
- Replace the P6 `ProfileViewModel.ownedCommunityCount` hardcoded `0` placeholder with a real `AdminCommunitiesRepository.fetchUserCommunitiesCount(userCode): Flow<Int>` call that re-emits whenever the user's owned-community set changes.
- Wire the shell for two new top-level routes (`community/create` + `community/admin/list`), both with bottom-bar-hidden behavior; add a `CommunityRoute` sealed class; route `CreateCommunityNavEvent.SubmitCompleted(code)` to the admin list.
- Pass the per-phase exit checklist (assembleDevDebug, installDevDebug on API 26 + API 35 emulators, no crashes, lint, unit tests, screenshots, demo walkthrough, `phase-7-end` tag).

**Non-Goals:**

- Premium / IAP (P8), Video Calling — Vonage / WebRTC (P9), Ranking + Deep Links + Polish (P10).
- Community-detail-as-admin screens (the iOS `CommunityDetailAsAdmin/` chain — documented as removed-dead-code in `screen-inventory.md` §"What is NOT in the Android port"). The Android port routes row taps to a `toast("Coming soon")` placeholder until a follow-up phase decides what's needed.
- The per-row swipe "Delete" admin action (iOS has it, but `screen-inventory.md` §10 does not include it for the Android port).
- Cover photo upload as a mandatory step (iOS already supports "Skip" via `CreateCommunityHomeVC` lines 100–125; Android mirrors — uploaded photos are optional, defaults to a gradient cover).
- pt-BR localization of all new screens (follow-up per `project-brief.md` §11.7 and `phases.md` Principle 7).
- Modification of the existing 9.1 orchestrator's three Start-buttons-do-everything UX beyond mirroring iOS (iOS surfaces three Start buttons as a power-user shortcut; the live demo flow is forward-only).
- Hooking community-creation analytics events (no analytics service is wired anywhere in the codebase yet; a follow-up can add Firebase Analytics).
- Optimistic local insert into the admin list after Submit (iOS waits for the Firestore listener to update the list; Android mirrors iOS's pessimistic sync).

## Decisions

### Decision: One `feature/community` module hosting both `create/` and `admin/` sub-packages

A single Gradle module hosts the entire P7 vertical, organized as `feature/community/create/` (screens 9.1–9.4) and `feature/community/admin/` (screens 10.1–10.3). This mirrors the per-tab boundary established in P2/P3/P4/P5 and keeps the shared `data/` package (the two repositories + the three Firestore APIs + the storage API) local to the module without round-tripping through `core/network`. The bottom-nav 5-tab shell does not yet expose "Communities" as its own tab (the iOS app does not either — `CommunityDetailAsAdmin` is reachable only from Profile); a future phase may split this into `feature/community/create` + `feature/community/admin` + `feature/community/detail` as the surface grows.

**Alternatives considered:**

- Two separate Gradle modules (`feature/community/create` + `feature/community/admin`). Rejected: shared `data/` (the two repositories + the storage API) would need a `core/community` or `core/data/community` module, adding a 7th top-level Gradle module for ~10 files of code. The sub-package split is sufficient.
- One `feature/account` module hosting Profile (P6) + Community (P7). Rejected: Profile and Community have distinct lifecycles (Profile is a bottom-nav tab; Community is two full-screen routes pushed from Profile), and the iOS source already separates them (`HomeVC/ProfileVC/` vs `HomeVC/CreateCommunity/` vs `HomeVC/CommunityDetailAsAdmin/`).

### Decision: `CommunitiesRepository.create(draft)` is a single atomic operation

iOS runs `generateCommunityCode` → `uploadCoverImage` → `setData(community)` as a flat `Promise`-chained sequence (`CommunitiesRepository.swift` lines 88–125). The Android port mirrors that: `suspend fun create(draft)` is a single `coroutineScope.launch { ... }` that runs the three steps sequentially inside the same coroutine so retry-on-error behavior matches iOS (a failed upload leaves a partial storage object, a failed write leaves nothing in Firestore — matching iOS exactly).

**Alternatives considered:**

- Split into three separate `suspend fun`s with the VM orchestrating them. Rejected: iOS keeps the upload + write as a single `communityRepo.createCommunity(...)` call; splitting adds an unprincipled seam between iOS and Android and complicates the VM's UDF contract.
- Use a Cloud Function `onCall("createCommunity")` that does the entire flow server-side. Rejected: iOS does the client-side flow; matching iOS is the source-of-truth rule. A future phase can move to a Cloud Function if the team decides.

### Decision: Cover image upload is optional (iOS's "Skip" affordance)

The iOS wizard has a "Skip" link on the cover photo step (`CreateCommunityHomeVC.swift` lines 100–125: "If the user does not provide an image, the community is created with a generated gradient cover"). The Android port mirrors this: skipping leaves `draft.coverBytes = null`, and on Submit the new community is created with both `firebaseImageURL` and `firebaseThumbnailURL` set to `null`. The `firebaseImageURL` projection falls back to the legacy `imageURL` IPFS field (which is `null` for new communities), then to the gradient fallback in `CommunityItemRow`. Mirrors iOS completely.

**Alternatives considered:**

- Make cover upload mandatory. Rejected: iOS does not; matching iOS is the source-of-truth rule. The Add Community wizard skips fast (matches the iOS UX); a follow-up phase can add a "Cover upload required" toggle if the team decides.
- Generate a default Bitmoji-style cover via an image-generation API. Rejected: iOS does not; matching iOS is the source-of-truth rule.

### Decision: AdminCommunitiesList per-row counts are cached in a Map on the VM

iOS computes member + nook counts per row on first appear via `communityRepo.fetchMemberCount(code) + fetchNookCount(code)`. The Android port mirrors that with `rowCounts: Map<communityCode, Pair<Int, Int>>` in `AdminCommunitiesListState`. On first load, `AdminCommunitiesListViewModel.init` runs `coroutineScope { async { fetchMemberCount } ; async { fetchNookCount } }` per row in parallel (mirrors iOS `Promise.all`), then writes the result to `rowCounts`. `CommunityItemRow` reads from `rowCounts[code] ?: (0, 0)` (mirrors iOS line 60 fallback).

**Alternatives considered:**

- Persist the counts on the `Communities/{code}` document as denormalized fields. Rejected: iOS does not denormalize; matching iOS is the source-of-truth rule. A follow-up can denormalize if Firestore read volume becomes a problem.
- Fetch counts lazily via Coil-style `LazyList` and only when the row scrolls into view. Rejected: iOS loads them eagerly; matching iOS is the source-of-truth rule. The list is bounded (admin-owned communities per user) and the eager approach is fine for the expected scale.

### Decision: `CreateCommunityStepLayout` is a single shared composable wrapping the three wizard steps

The wizard chrome (top app bar with back button + "Create Community" title + step indicator, Continue pill button at the bottom, content slot in the middle) is identical across 9.2, 9.3, 9.4. The iOS app implements each step as its own VC with a similar layout (each VC has a Continue button at the bottom-right and a back button in the nav bar). The Android port factors this into one `CreateCommunityStepLayout` composable that takes the per-step content slot + a `ContentState` data class (continuing: Boolean, isSubmitting: Boolean), keeping all three step destinations DRY without losing visual parity. Mirrors iOS without surfacing the underlying code duplication.

**Alternatives considered:**

- Three separate composables per step with duplicated chrome. Rejected: iOS has the same code duplication (each step is a separate VC); matching iOS does not require preserving the duplication. One shared composable is the idiomatic Compose pattern.
- One single composable per step that receives a `Step` enum and `when`-switches internally. Rejected: leaves the per-step state machine (`CreateCommunityNameState` vs `CreateCommunityDescriptionState` vs `SelectCommunityThemeState`) ungainly; the DRY win is small relative to the loss of clear per-step UDF contracts.

### Decision: `Community` Firestore read uses kotlinx-datetime for the `Timestamp` mapping

iOS uses `Timestamp.dateValue()` to convert the Firestore Timestamp to a Swift `Date` for the created-at sort. The Android port uses `kotlinx-datetime`'s `Instant` mapping via the `firebase-firestore-ktx` + `kotlinx-datetime` integration added in P3 (per `korner-domain` spec). `Community.createdAtTimestamp: Timestamp?` is the raw Firestore representation; `Community.createdAt: Instant?` is the derived kotlinx-datetime projection. Matches the P3 pattern for Korner + Nook timestamps.

**Alternatives considered:**

- Use `java.time.Instant` (JVM). Rejected: not multiplatform-safe; the project has standardized on kotlinx-datetime since P3.
- Map `Timestamp` directly to `kotlinx.datetime.Instant` in the `@SerialName` annotation. Rejected: Firestore-KTX types are not `@Serializable`-able; the explicit two-field approach (raw + derived) is the canonical P3 pattern.

### Decision: `CommunitiesRepository.fetchOwnedByUser` composes via a Firestore batch read + parallel count queries

iOS `AdminCommunityListController.viewDidLoad` does the initial `Communities.where(userId).get()` then iterates the result and calls `fetchMemberCount` + `fetchNookCount` per community in a `Promise.all` block. The Android port mirrors that: `Communities.whereEqualTo("userId", userCode).orderBy("createdDate", DESC).get()` returns the initial list, then `coroutineScope { async { fetchCount(code, MEMBERS) } ; async { fetchCount(code, NOOKS) } }` per row in parallel via `Dispatchers.IO`. The list sorts `createdAtTimestamp DESC` matching iOS line 41. The `queryLimit = 50` follows iOS line 45 (the admin list is per-user, so 50 is more than enough headroom; pagination is **not** added in P7 because iOS has no pagination either — matching iOS is the source-of-truth rule).

**Alternatives considered:**

- Paging 3 `Pager` for the admin list. Rejected: iOS does not paginate; matching iOS is the source-of-truth rule. A user is unlikely to own more than 5–10 communities.
- Firestore `count()` aggregation for the member + nook projections. Rejected: iOS does `whereEqualTo("communityId", code).get().count()` via a full documents fetch; matching iOS avoids introducing an aggregation-not-yet-enabled-in-shared-firebase risk.

### Decision: Wizard step back navigation preserves the in-memory draft

iOS `CreateCommunityHomeVC` holds the draft in instance variables (`communityName`, `communityDescription`, `communityTheme`) and survives per-step back navigation. The Android port holds the draft in `CreateCommunityViewModel.communityDraft: MutableStateFlow<CommunityDraft>` and the per-step VMs write back to the orchestrator on `Advance(name|description|theme)` via a callback or a shared `SavedStateHandle`. The draft survives a back-tap from 9.3 to 9.2 (the user can revisit 9.2 to fix the name without losing their description progress), and is discarded when the user pops the orchestrator all the way back to Profile. Mirrors iOS exactly.

**Alternatives considered:**

- Persist the draft to DataStore. Rejected: iOS does not persist mid-wizard; matching iOS is the source-of-truth rule. The Android port's `SavedStateHandle` would survive process death within a single wizard session but not across full app kills — matching iOS's transient draft behavior.
- Use a `SavedStateHandle` per step with a parent orchestrator handling the merge. Rejected: more state machinery than iOS requires; the single `MutableStateFlow` on the orchestrator is the simpler, parity-preserving choice.

### Decision: Community creation Skip + cover photo both produce a gradient-only cover when no image is provided

When the user either (a) skips the cover photo or (b) does not visit 9.4's upload tile, the resulting community is created with `firebaseImageURL = null` + `firebaseThumbnailURL = null`. The `CommunityItemRow` renders the theme's `gradientBrush` as the placeholder. Mirrors iOS `CommunityItemView.swift` lines 50–62 (which renders a colored circle when the image URL is nil, picking from `community.theme` colors).

**Alternatives considered:**

- Auto-generate a deterministic gradient based on `communityCode.hashCode()`. Rejected: iOS uses the community's `theme` gradient; matching iOS is the source-of-truth rule.
- Render a single neutral color placeholder regardless of theme. Rejected: iOS uses the theme colors; matching iOS is the source-of-truth rule.

### Decision: `CreateCommunityViewModel.submit()` uses a single `coroutineScope { launch { ... } }` with retry-once-on-network-only

The Submit flow runs `generateCode` → `uploadCover` (if `coverBytes != null`) → `writeCommunity` sequentially. On network failure (but not on validation failure), the flow retries once with exponential backoff (1s → 2s, matching iOS's `URLSession` retry convention). On validation failure (`code already exists`, `firebase storage forbidden`), the flow surfaces `AppError` without retry. Mirrors iOS `CreateCommunityHomeVC.submitForm` lines 130–145.

**Alternatives considered:**

- No retry. Rejected: iOS's `URLSession.shared.dataTask` effectively retries on transient network errors via the OS-level retry layer; matching iOS means the Android port retries once for parity.
- Retry forever. Rejected: matches iOS's bounded-retry convention; an unbounded retry would mask legitimate `code already exists` errors.

### Decision: Account Deletion Plumbing (from P6) remains unchanged

P6 already added `UserProfileRepository.deleteAccount()` + `AccountDeletionViewModel` + the `userDeletedHisAccount` event bus. P7 does not modify them. The event bus continues to be collected by `ShellViewModel` to route back to auth on emission. No new P7 work is needed here. Mirrors P6 + iOS parity.

**Alternatives considered:**

- N/A — the P6 decision carries forward verbatim.

## Risks / Trade-offs

- [Cover photo upload is optional and produces a `null` `firebaseImageURL`] → Mitigation: documented behavior matching iOS; the `Community` `coverImageURL` fallback chain handles the `null` case (gradient placeholder rendered via theme colors).
- [6-char community code can collide under heavy load (birthday-paradox collision probability ~1% at ~10,000 communities)] → Mitigation: iOS uses the same generation scheme; matching iOS is the source-of-truth rule. A future phase can add a uniqueness check via `Communities.whereEqualTo("idCommunity", proposedCode).get()` + retry-on-collision.
- [Per-row count queries (member + nook) on the admin list mean 2N reads per list load] → Mitigation: iOS does the same; matching iOS is the source-of-truth rule. A future phase can denormalize the counts onto the `Communities/{code}` document. The list is bounded (per-user owned communities, expected N < 20), so the 2N reads are negligible.
- [Admin-owned-communities row tap routes to a `toast("Coming soon")` placeholder because community-detail-as-admin is removed-dead-code] → Mitigation: documented behavior per `screen-inventory.md` §"What is NOT in the Android port"; a future phase can add the iOS CommunityDetailAsAdmin chain if the team decides.
- [Pull-to-refresh re-fetches all per-row counts and may briefly show stale counts] → Mitigation: iOS has the same briefly-stale behavior on refresh; matching iOS is the source-of-truth rule. The `rowCounts` map is replaced in a single `state.update { ... }` call so the visual flicker is one frame.
- [The 9.1 orchestrator has three "Start" buttons but the demo flow is forward-only; the Start buttons are a power-user shortcut] → Mitigation: documented behavior matching iOS; the primary demo flow is Start button 1 (Name) → Continue → Description → Continue → Theme → Confirm → Admin List.
- [The `Cover photo` upload uses `Bitmap.compress(JPEG, 70, ...)` quality which is the Android equivalent of iOS's `jpegData(compressionQuality: 0.5)`] → Mitigation: documented mapping per P6 + Android `Bitmap.compress` docs; produces visually equivalent output.
- [The "communities owned" count in `UserInfoHeader` re-emits via Firestore listener, not via a Cloud Function] → Mitigation: iOS uses `NotificationCenter.post(.profileUpdatedNotification)` which is essentially the same intra-app re-emit pattern; matching iOS is the source-of-truth rule. Cross-device sync (server-side push) is not added.
- [pt-BR localization of all new screens is held for a follow-up] → Mitigation: documented behavior per `project-brief.md` §11.7 + `phases.md` Principle 7; the screens are RTL-safe so a future pt-BR translation will not require layout changes.
- [The profile menu now has six rows (Notifications, My Data, Create Community, My Communities conditionally, Privacy Policy, [Logout and Delete Account held for P10])] → Mitigation: matches iOS's full menu (lines 75–110) with only the stakeholder-deferred rows stripped; behavior parity preserved. A follow-up can wire the two P10 rows.
- [Firebase Storage v4 signed URLs may 403 if `firebaseStorageService` IAM is misconfigured] → Mitigation: P6's `UserStorageApi.uploadAvatar` already exercises the same pipeline and P7 inherits the same IAM setup; if P6's pipeline works in production (verified by stakeholders), P7's pipeline works identically.

## Migration Plan

Phase 7 lands as a single PR landing the new feature module + the model + the repositories + the storage API + the per-row composables + the wizard VMs + the shell wiring. There is no backend migration; every Firestore write matches the iOS app against `kornerz-72fba`. The new `Community` model is written via `setData(community)` (overwrite), not via merge — meaning new communities can be created cleanly without `idCommunity` collisions with any pre-existing documents. Rollback is a `git revert` of the merge commit and re-tagging `phase-6-end`; no data migration is required because P7 is read/write against the same fields iOS already populates.

## Open Questions

1. **Community code generation length** — iOS uses 6-char alphanumeric; the Android port mirrors. If the team wants a longer code (e.g., 8 chars) for uniqueness headroom, the change is one-line. Current decision: 6 chars (matches iOS).
2. **Admin list query limit** — set to 50; iOS has no explicit limit but the practical count is bounded (per-user). Current decision: 50 with no pagination (matches iOS).
3. **Cover photo defaults** — iOS uses the community's theme gradient. The Android port mirrors. If the team wants a separate "no cover" icon (a generic mountains glyph), the change is one composable swap.
4. **Skip-back-to-Profile behavior** — iOS confirms with an alert ("Your changes will be lost. Continue?"). The Android port mirrors but could ship without the confirm dialog for the first cut. Current decision: ship without confirm for v1; matches the simpler P6 profile-navigation flow.
5. **Community name maxLength** — iOS uses `maxLength = 80`. The Android port mirrors. If the team wants a shorter limit (e.g., 40 chars), the change is one-line.
