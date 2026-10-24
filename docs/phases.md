# Kornerz Android — Phased Delivery Plan

**Source of truth:** `docs/screen-inventory.md` (77 live screens), `docs/project-brief.md` (decisions)
**Goal:** every phase ships an **APK that compiles, launches, and is presentable to stakeholders** as a demo.

---

## Principles

1. **Vertical slices.** Each phase produces a usable chunk of the app, not a horizontal layer. A phase can be installed and clicked through end-to-end for its feature area.
2. **No dead code.** The iOS source has 123 dead screens excluded from this plan (see `screen-inventory.md` §"What is NOT in the Android port"). Only live screens ship.
3. **No dead tabs.** If a tab isn't ready in a phase, it isn't in the tab bar that phase — not stubbed, not visible.
4. **Each phase is a clean Gradle state.** `git tag phase-N-end` marks a launchable artifact.
5. **Backend integration is real.** Every phase uses the shared Firebase project (`kornerz-72fba`) — no mock servers.
6. **Design tokens land in P0.** Once tokens exist, every subsequent screen uses them. No per-screen color hex values.
7. **English only.** Mirrors §11.7 of `project-brief.md`. pt-BR comes later.

---

## Phase overview

| Phase | Codename | Live screens | Demoable claim |
|---|---|---|---|
| **P0** | Foundation | 0 | App boots, shows 5 empty tabs with branded theme, splash, app icon. |
| **P1** | Auth & Onboarding | 16 | Full sign-up, login, password reset, force-update. |
| **P2** | Home & Tab Shell | 16 | Home tab fully populated with sections + carousels. |
| **P3** | Kornerz Tab | 10 | Browse Kornerz, view Korner detail (about, nooks, members). |
| **P4** | Nooks Tab | 3 | Browse Nooks tab (live + scheduled). |
| **P5** | Loops Tab | 10 | Loop feed, detail, comments, reply threads. |
| **P6** | Profile & Notifications | 7 | Own profile, edit, notifications, privacy policy. |
| **P7** | Create Community & Admin | 7 | Create community wizard, admin owned-communities list. |
| **P8** | Premium / IAP | 3 | Subscribe via Google Play Billing, premium gating fires. |
| **P9** | Video Calling | 4 | Join community video room via Vonage. |
| **P10** | Ranking + Deep Links + Polish | 8 | Klouders leaderboard, deep links, polish pass. |

**Total: ~84 deliverables (77 screens + 7 coordinators/infrastructure items) across 11 phases.**

---

## Phase 0 — Foundation

**Codename:** *Hello Kornerz*
**Tag:** `phase-0-end`

### What's demoable
App installs and launches. Splash screen fades into a 5-tab bottom navigation shell with the Kornerz theme (colors, Satoshi font, dark/light modes). Each tab shows an empty state. App icon and splash branded.

### Deliverables
- Android project structure (Gradle KTS, version catalog)
- Module split per `project-brief.md` §4.2
- Min SDK 26, target SDK = latest stable
- Compose BOM pinned, Material 3 theme, Hilt, Navigation-Compose, Coil
- `core/ui/`: design tokens (colors mapped from iOS `Config.swift`), Satoshi font, typography, shape, spacing (8pt grid)
- `core/model/`: empty placeholder
- `core/network/`: Firebase init
- `core/storage/`: empty placeholders
- `feature/splash/`: branded splash screen
- `feature/shell/`: `MainActivity` + 5-tab `NavHost` with empty placeholders
- Build flavors: dev + prod (mirrors `KornerzDev` vs `Kornerz`)
- CI runs `assembleDevDebug` on PR

### Compilable? ✅ APK builds.
### Presentable? ✅ Stakeholders see app boot, theme, tabs.

---

## Phase 1 — Auth & Onboarding

**Codename:** *Identity*
**Tag:** `phase-1-end`

### What's demoable
Full sign-up, login, and password recovery flows. Unauthenticated user can create an account through the 9-step signup wizard and lands on the (still empty) Home tab. Session persists across launches. Force-update sheet triggers from Remote Config.

### Deliverables
- `core/model/`: `User`, `CountryCodes`
- `core/network/`: custom-auth Cloud Function client
- `core/storage/`: DataStore-based session
- `feature/auth/`:
  - Splash (1.1) → router decides login vs main
  - Login (1.2)
  - Signup wizard (1.3 – 1.12): full name → nickname → email → password → phone → country code → language → Kornerz interests → photo
  - Forgot password (1.13), confirm code / OTP (1.14), change password (1.15)
  - Privacy policy inline (1.12)
- `feature/shell/`: after auth, route to tabs (still empty P0 placeholders)
- FCM token registration (plumbing, no UI yet)
- Custom font loading + RTL-safe layouts

### Compilable? ✅ APK builds, login → empty tabs.
### Presentable? ✅ "Here's the signup flow, login, force-update."

---

## Phase 2 — Home Tab & Tab Shell

**Codename:** *First Impression*
**Tag:** `phase-2-end`

### What's demoable
The Home tab is fully populated with all sections. A logged-in user sees their avatar, greeting, loop-of-the-day, recent articles, recent loops, upcoming nooks, and their Kornerz. Other tabs still show empty states.

### Deliverables
- `feature/home/`:
  - Home tab root (3.1)
  - Home header (3.2)
  - Home feed section composer (3.3)
  - Loop of the day (3.4)
  - Recent articles (3.5)
  - Recent loops (3.6)
  - Upcoming nooks (3.7)
  - Your Kornerz (3.8)
  - Kornerz carousel (3.9)
- Repositories: read-only fetches for home feed
- Image loading (Coil) with shimmer placeholders

### Compilable? ✅ APK builds, Home tab loads real data.
### Presentable? ✅ "This is the home tab, here are all the sections."

---

## Phase 3 — Kornerz Tab

**Codename:** *Browse Korners*
**Tag:** `phase-3-end`

### What's demoable
Kornerz tab fully works. Browse Korners, tap one, see its detail page (cover, title, CTA, about, current nook, nooks list, members).

### Deliverables
- `core/model/`: `Korner`
- Repositories: `KornerzRepository`
- `feature/kornerz/`:
  - Kornerz tab root (4.1) + shared header (4.2)
  - Korner detail host + view (4.3, 4.4)
  - Korner detail header (4.5)
  - About (4.6)
  - Current nook (4.7)
  - Nooks list (4.8), single nook tile (4.9)
  - Members (4.10)

### Compilable? ✅ APK builds, Kornerz tab works.
### Presentable? ✅ "Browse Korners, view Korner detail."

---

## Phase 4 — Nooks Tab

**Codename:** *Nooks*
**Tag:** `phase-4-end`

### What's demoable
Nooks tab populated with live and scheduled nooks.

### Deliverables
- `feature/nooks/`:
  - Nooks tab root (5.1)
  - Single nook card (5.2)
  - Scheduled/upcoming nook (5.3)
- Repository: `NooksRepository`

### Compilable? ✅ APK builds.
### Presentable? ✅ "Browse Nooks tab."

---

## Phase 5 — Loops Tab

**Codename:** *Loops*
**Tag:** `phase-5-end`

### What's demoable
Loops tab: scroll a feed of loops, filter by chips, tap one, view loop detail (video + comments), comment, reply to comments.

### Deliverables
- `core/model/`: `Loop`, `LoopComment`, `LoopLike`
- Repositories: `LoopRepository`, `LoopCommentRepository`, `LoopLikeRepository`
- `feature/loops/`:
  - Loops tab root (6.1)
  - Loops filter chips (6.2)
  - Single loop item (6.3)
  - Loop detail host + view (6.4, 6.5)
  - Single loop comment (6.6)
  - Create comment host + view (6.7, 6.8)
  - Reply thread host + view (6.9, 6.10)
- Video playback (ExoPlayer wrapped in Compose)

### Compilable? ✅ APK builds, Loops tab works.
### Presentable? ✅ "Watch loops, comment, reply."

---

## Phase 6 — Profile & Notifications

**Codename:** *Account*
**Tag:** `phase-6-end`

### What's demoable
Own profile view, edit basic info (single-field change), privacy policy PDF viewer, in-app notifications list.

### Deliverables
- Repositories: `UserProfileRepository`, `NotificationRepository`
- `feature/profile/`:
  - User profile (7.1)
  - Edit basic info (7.2)
  - Change basic info single field (7.3)
  - Change basic info success popup (7.4)
  - Privacy policy PDF viewer (7.5)
- `feature/notifications/`:
  - Notifications host (8.1)
  - Notifications feed (8.2)
- FCM: receive + display push (Android 13+ POST_NOTIFICATIONS permission flow)
- Account deletion flow (mirrors `.userDeletedHisAccount` in iOS `AppTabController`)

### Compilable? ✅ APK builds.
### Presentable? ✅ "Profile editing, notifications, privacy."

---

## Phase 7 — Create Community & Admin

**Codename:** *Build*
**Tag:** `phase-7-end`

### What's demoable
From Profile → Admin Community List (only visible if user owns at least one community), create a new community through the 3-step wizard (name → description → theme). Community appears in the user's admin list.

### Deliverables
- `core/model/`: `Community`, `CommunityAdmin`
- Repositories: `CommunitiesRepository` (create side), `AdminCommunitiesRepository`
- `feature/community/create/`:
  - Create community home (9.1)
  - Name (9.2), Description (9.3), Theme picker (9.4)
- `feature/community/admin/`:
  - Admin community list host (10.1)
  - Admin community list view (10.2)
  - Admin community list row (10.3)
- Image upload (Firebase Storage via v4 signed URLs — mirrors iOS `FirebaseStorageService`)

### Compilable? ✅ APK builds, create-community wizard works.
### Presentable? ✅ "From profile, create a new community."

---

## Phase 8 — Premium / IAP

**Codename:** *Premium*
**Tag:** `phase-8-end`

### What's demoable
Subscription plans screen, purchase via Google Play Billing, successful purchase updates `User.isUserPremium` + `subscriptionPlatform = "Android"`. Premium gating works (the iOS `AppTabController.showSubscriptionScreen()` gate fires for free users post-trial).

### Deliverables
- `core/model/`: full `User` (with `subscriptionExpiryDate`, `subscriptionType`, `subscriptionPlatform`)
- Repositories: `UserRepository.setUserPremium`
- `feature/subscription/`:
  - Subscription plans host (11.1)
  - Subscription plans view (11.2)
  - Subscription success (11.3)
- Google Play Billing v6+ integration
- RTDN (Real-Time Developer Notifications) webhook → Cloud Function updates user
- Server-side verification of purchases via Google Play Developer API
- Trial period gate (7 days, mirrors `Constants.trialPeriodInSeconds`)

### Compilable? ✅ APK builds, subscription flow works end-to-end.
### Presentable? ✅ "Subscribe on Android, see premium features unlock."

---

## Phase 9 — Video Calling

**Codename:** *Connect*
**Tag:** `phase-9-end`

### What's demoable
Tap "Join" on a community video room (reached from Korner detail, once a Korner exposes a video room entry point). Publisher tile (local camera) + subscriber tiles (remote participants) render correctly. Mute, camera toggle, leave controls all functional.

### Deliverables
- `core/model/`: `Room`, `Participant`, `Publisher`, `ConnectionCountResponse`, `SessionsInfoResponse`
- `core/network/`: WebSocket client (OkHttp WebSocket — mirrors iOS `WebSocketService.swift` + `OpenTokTokenService.swift`)
- `feature/videocall/`:
  - Video call view (12.1)
  - Video call loading state (12.2)
  - Publisher container (12.3) — `AndroidView` wrapper around Vonage `Publisher.view`
  - Subscriber container (12.4) — same pattern
- Vonage Android SDK 2.34.0 dependency
- Permissions: CAMERA, RECORD_AUDIO, BLUETOOTH_CONNECT, MODIFY_AUDIO_SETTINGS (per Vonage docs)
- Foreground service for ongoing call (mirrors `Basic-Video-Chat-With-ForegroundServices` sample)

### Compilable? ✅ APK builds, video calls functional.
### Presentable? ✅ "Two phones join a community room, see each other."

---

## Phase 10 — Ranking + Deep Links + Polish

**Codename:** *Complete*
**Tag:** `phase-10-end`

### What's demoable
- Klouders (Ranking) tab shows top users / gamification leaderboard.
- Push notifications deep-link to the right screen.
- Empty states, error states, retry views, no-data views polished.
- Accessibility pass.

### Deliverables
- `core/model/`: `Gamification`, `Rank`, `UserNotification`
- Repositories: `GamificationService`
- `feature/ranking/`:
  - Ranking host (2.6)
  - Ranking view (2.7)
- `feature/shell/`:
  - Deep-link interstitial (13.1)
  - Deep-link routing in main activity (cold start + warm)
- Polish:
  - Empty-state illustrations per tab
  - Retry views for failed network calls (mirrors `RetryView`)
  - No-data views (`NoDataView`)
  - Loading-more views (`LoadingMoreView`)
  - Accessibility: TalkBack labels, content descriptions, font-scale respect
  - Performance pass: cold-start < 2 s, scroll smoothness
  - Crash-free sessions ≥ 99.5% SLO
  - ProGuard / R8 rules verified for Vonage + Firebase

### Compilable? ✅ APK builds.
### Presentable? ✅ "Full feature parity with iOS."

---

## Phase dependency graph

```
        ┌─────┐
        │ P0  │  Foundation (no dependencies)
        └──┬──┘
           │
        ┌──▼──┐
        │ P1  │  Auth (depends on P0)
        └──┬──┘
           │
   ┌───────┼───────┬───────┐
   │       │       │       │
┌──▼─┐  ┌──▼─┐  ┌──▼─┐  ┌──▼─┐
│ P2 │  │ P3 │  │ P5 │  │ P6 │  (all depend on P1; can run in parallel)
│Home│  │Korn│  │Loop│  │Prof│
└──┬─┘  └──┬─┘  └──┬─┘  └──┬─┘
   │       │       │       │
┌──▼─┐  ┌──▼─┐  ┌──▼─┐  ┌──▼──┐
│ P4 │  │ P8 │  │ P9 │  │ P7  │
│Nook│  │IAP │  │Video│ │Admin │
└──┘  └──┬──┘  └────┘ └──────┘
         │
         ┌─────────┐
         │   P10   │   Ranking + deep links + polish (depends on all above)
         └─────────┘
```

**Parallelizable tracks after P1:**
- Track A: P2 (Home) — independent
- Track B: P3 (Kornerz) → P4 (Nooks) — sequential
- Track C: P5 (Loops) — independent
- Track D: P6 (Profile + Notifications) → P7 (Create Community & Admin) — sequential (admin is reached from profile)
- Track E: P8 (Premium / IAP) — independent
- Track F: P9 (Video Calling) — depends on P3 (needs a Korner context to anchor video rooms)

Then P10 closes out the work.

---

## Per-phase exit checklist

Every phase must pass **all** of these before tagging:

- [ ] `./gradlew :app:assembleDevDebug` succeeds
- [ ] APK installs via `./gradlew :app:installDevDebug` on a Pixel-class emulator running API 26 and API 35
- [ ] App launches without crash (verified via `adb logcat`)
- [ ] All screens in scope render with no missing assets / no TODO strings
- [ ] Lint passes (`./gradlew :app:lintDevDebug`)
- [ ] New repositories exercised against the real backend (manual check in the running app)
- [ ] One screenshot per in-scope screen attached to PR
- [ ] One short Loom/walkthrough demo recorded (≤ 5 min) attached to PR
- [ ] `phase-N-end` git tag created
- [ ] Demo-ready APK shared with PM/QA for review

---

## What "presentable" actually means per phase

| Phase | Demo audience | Demo script |
|---|---|---|
| P0 | Engineering, Design | App boots, theme matches iOS brand, 5 tabs visible. |
| P1 | PM, Engineering | Create account end-to-end, log out, log back in, see force-update sheet. |
| P2 | PM, Marketing, Design | Open app, scroll Home tab, show every section populated with real data. |
| P3 | PM, Design | Browse Kornerz tab, view a Korner detail page. |
| P4 | PM, Design | Browse Nooks tab, view live + scheduled nooks. |
| P5 | PM, QA | Scroll Loops, open one, comment, reply. |
| P6 | PM, QA | Edit profile, change a field, view notifications, open privacy policy. |
| P7 | PM, Community ops | Create a new community through the wizard. |
| P8 | PM, Finance, QA | Subscribe via Google Play, see premium unlock. Cancel, see it expire. |
| P9 | PM, Engineering, QA | Two devices join a community video room. |
| P10 | All stakeholders | Feature-by-feature parity demo against iOS. |

---

## Open questions before kickoff

1. **Team shape** — how many Android engineers? Affects how many phases can run in parallel.
2. **CI/CD target** — GitHub Actions, Bitrise, Fastlane?
3. **App signing** — where do upload keys live? Dev vs prod signing strategy.
4. **Firebase project access** — Android team needs Editor role on `kornerz-72fba`.
5. **Vonage credentials** — Android app needs its own Vonage project or share with iOS? (Session tokens are per-app.)
6. **Google Play Console** — paid app or free? Subscription product setup.
7. **Distribution during development** — Internal Testing track in Play Console for QA builds.
8. **P9 entry point** — the iOS app's `VideoCallView` is reached from multiple coordinators. Confirm the Android entry point: from Korner detail? From a video CTA on a Loop? Define this in P9 design.

---

## Out of scope for the Android port

These iOS screens / features are **dead code** or **not in app's active flow** and intentionally **not** ported.

### Removed during initial scoping (123 dead files)
See `docs/screen-inventory.md` §"What is NOT in the Android port" — full list including:
- Article / Video / Event / Forum creation wizards (~30 screens)
- Legacy `LoginVC`, `NotificationVC`, `AppHomeVC`, `HomeVC`
- Half-built `ForumTabController` (defined but never wired)
- Legacy in-app purchase screens
- Legacy carousels, cells, video player
- Legacy community detail chain

### Removed during stakeholder review (2026-10-07)
The following screens are referenced somewhere in the source tree but have **no entry point in the active user flow** (confirmed by stakeholders):
- Forum Detail (`HomeVC/ForumDetail/`) — 3 screens
- Post Detail (`PostDetailController.swift` + `PostDetailScreen.swift`) — 2 screens
- Event Detail + RSVP (`HomeVC/CommunitiesVC/Events/EventDetailVC/`) — 4 screens
- **Subtotal: 9 screens**

**Note:** the iOS source files for these screens still exist and may be referenced by dead code paths. They are not in the Android port. If the iOS app later wires them back into the flow, they can be added as a follow-up phase.

---

## References

- `docs/project-brief.md` — decisions, architecture, scope
- `docs/screen-inventory.md` — 77-screen live catalog + dead-code list
- `kornerz/` — iOS source of truth
- `kornerz/FIREBASE_STORAGE_MIGRATION.md` — current storage migration status
- `github.com/Vonage/vonage-video-android-sdk-samples/JetpackCompose/` — reference Vonage Compose samples