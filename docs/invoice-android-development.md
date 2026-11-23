# INVOICE

**Invoice No.:** KZ-AND-001
**Issue Date:** 19 November 2026
**Currency:** USD
**Project:** Kornerz — Native Android Application Development

---

## Invoice To

| Field | Detail |
|---|---|
| Company | _Client name_ |
| Address | _Client address_ |
| Contact | _Client contact_ |
| Email | _Client email_ |

## From

| Field | Detail |
|---|---|
| Developer / Company | _Your name or studio name_ |
| Address | _Your address_ |
| Email | _Your email_ |
| Phone | _Your phone_ |

---

## Project Overview

Complete development of the **Kornerz** native Android application from scratch — a social-community platform comprising communities ("Kornerz"), nooks, loops/forums, video calls, gamification, notifications, and premium subscriptions.

**Technology:** Kotlin 2.0.21 · Jetpack Compose (Material 3) · MVVM + Repository + Unidirectional Data Flow · Hilt · Coroutines/Flow · Navigation-Compose · Firebase (Firestore, Functions, Storage, Messaging, Config) · Google Play Billing 8.0.0 · Vonage/OpenTok 2.34.0 · Media3 ExoPlayer · Coil · OkHttp

**Platform targets:** `minSdk 26` · `compileSdk 36` · `targetSdk 36` · JDK 17 · AGP 8.8.0

**Delivered scope:** 18 Gradle modules · **77 production screens** · 50 documented capability specifications · 20+ Firestore collections · 5 Cloud Functions · shared Firebase backend `kornerz-72fba` (same user base and content graph as the iOS client)

---

## Rate

| | |
|---|---|
| **Hourly rate** | **USD 25.00 per hour** |
| **Billing basis** | Actual engineering hours applied by a single senior Android developer |
| **Currency** | USD |

---

## Line Items

All tasks are ≤ 8 hours. Each row is a distinct, independently verifiable deliverable.

---

### Area 1 — Project Foundation, Architecture & Build System

| # | Task | Hours | Cost |
|---|---|---:|---:|
| 1.1 | Initialize Gradle multi-module project — root build script, settings module registry, AGP 8.8.0 / Kotlin 2.0.21 / JDK 17 toolchain | 4 | $100 |
| 1.2 | Create version catalog `gradle/libs.versions.toml` — declare and pin every plugin and dependency version | 3 | $75 |
| 1.3 | Create 4 core modules — `core:model` (pure Kotlin/JVM), `core:network`, `core:storage`, `core:ui` | 3 | $75 |
| 1.4 | Create 13 feature modules + `app` module — package structure, manifest, ProGuard rules, dependency wiring | 4 | $100 |
| 1.5 | Configure build variants — `dev`/`prod` product flavors on `env` dimension, flavour-specific `google-services.json` | 2 | $50 |
| 1.6 | Configure `debug`/`release` build types — R8 minification, resource shrinking, ProGuard keep rules | 2 | $50 |
| 1.7 | Configure Hilt dependency injection — `Application` class, kapt config, DI module registry | 3 | $75 |
| | **Area 1 subtotal** | **21** | **$525** |

---

### Area 2 — Design System & Shared UI Components

| # | Task | Hours | Cost |
|---|---|---:|---:|
| 2.1 | Define colour palette — 46 brand colours mapped 1:1 from the iOS design tokens (light + dark) | 4 | $100 |
| 2.2 | Define community gradient palette — 20 community accent colours with hex↔Color resolution | 2 | $50 |
| 2.3 | Bundle Satoshi typeface and define the full Material 3 typography scale | 3 | $75 |
| 2.4 | Define shape and spacing tokens — 4 corner radii and the 8-point spacing grid | 2 | $50 |
| 2.5 | Build `KornerzTheme` — light/dark colour schemes, typography, shapes, with previews | 2 | $50 |
| 2.6 | Build button, text field, search field and OTP field components | 3 | $75 |
| 2.7 | Build avatar, empty state, no-data, retry and loading components | 3 | $75 |
| 2.8 | Build rich-text renderers, clipped-shape image tiles and the `AsyncValue` state renderer | 3 | $75 |
| 2.9 | Build image picker, force-update sheet, header and nook card components | 2 | $50 |
| | **Area 2 subtotal** | **24** | **$600** |

---

### Area 3 — Data Layer, Models & Backend Integration

| # | Task | Hours | Cost |
|---|---|---:|---:|
| 3.1 | Define core model classes — User, Community, Korner, Nook, Loop, Forum (38 model files total) | 4 | $100 |
| 3.2 | Define `AsyncValue<T>` sealed interface as the standard UI state type | 1 | $25 |
| 3.3 | Define video-call, subscription, gamification and deep-link model sets | 2 | $50 |
| 3.4 | Configure Firebase — Firestore, Functions, Storage, Messaging, Config, Crashlytics, Auth | 2 | $50 |
| 3.5 | Implement `FirestoreAuthApi` and `AuthRepository` — custom Firestore-verified auth mirroring the iOS model | 3 | $75 |
| 3.6 | Implement `UsersRepository` + `UserFirestoreApi` — user reads by email/code, profile writes | 2 | $50 |
| 3.7 | Implement `KornerFirestoreApi` and `CommunitiesRepository` — community reads across `Communities` and `CommunityAdm` | 3 | $75 |
| 3.8 | Implement `NookFirestoreApi` + `NooksRepository` — live and upcoming event queries | 2 | $50 |
| 3.9 | Implement `LoopFirestoreApi` — feed, search, detail, recent, by-publisher, by-community (paged) plus write path | 4 | $100 |
| 3.10 | Implement `LoopCommentFirestoreApi`, `LoopLikeFirestoreApi` and `CommentLikeFirestoreApi` | 3 | $75 |
| 3.11 | Implement `FellowRepository` — follow / unfollow / follower list / exists check | 2 | $50 |
| 3.12 | Implement `NotificationFirestoreApi` and `GamificationRepository` — paginated feed, monthly points, ranking aggregation | 2 | $50 |
| 3.13 | Implement `FirebaseStorageUploader` — signed-URL upload, segmented 64 KB PUT, progress callback | 2 | $50 |
| 3.14 | Implement `SessionInfoApi` and `VonageSessionController` — session, publisher, subscriber lifecycle | 2 | $50 |
| 3.15 | Implement `SessionTokenService` — OkHttp WebSocket token handshake with timeout and clean close | 2 | $50 |
| 3.16 | Implement DataStore session repository, country-code catalogue reader and FCM device-token stores | 2 | $50 |
| 3.17 | Wire network, messaging, remote-config and video-call Hilt modules | 2 | $50 |
| | **Area 3 subtotal** | **40** | **$1,000** |

---

### Area 4 — Authentication & Onboarding

| # | Task | Hours | Cost |
|---|---|---:|---:|
| 4.1 | Build welcome / splash screen with iOS parity | 2 | $50 |
| 4.2 | Build login screen with email/password validation | 2 | $50 |
| 4.3 | Implement login error taxonomy — `InvalidCredentials`, `EmailAlreadyInUse`, `NicknameAlreadyInUse`, `InvalidCode`, `NetworkError`, `SessionExpired` | 2 | $50 |
| 4.4 | Build 8-step signup wizard shell — navigation, step state, progress tracking | 3 | $75 |
| 4.5 | Build signup steps 1–4 — full name, email uniqueness, phone + country dial-code, language multi-select | 4 | $100 |
| 4.6 | Build signup steps 5–8 — Kornerz interests grid, nickname uniqueness, profile photo, password | 4 | $100 |
| 4.7 | Build forgot-password, OTP confirmation and change-password screens | 3 | $75 |
| 4.8 | Build custom privacy-policy PDF viewer on `android.graphics.pdf.PdfRenderer` | 3 | $75 |
| 4.9 | Implement session persistence, auto logout on expiry and the Remote Config force-update gate | 2 | $50 |
| | **Area 4 subtotal** | **25** | **$625** |

---

### Area 5 — App Shell, Navigation & Deep Linking

| # | Task | Hours | Cost |
|---|---|---:|---:|
| 5.1 | Build 5-tab bottom navigation bar — Home, Kornerz, Nooks, Loops, Klouders | 2 | $50 |
| 5.2 | Implement per-tab nested `NavHost`s with state save and restore | 3 | $75 |
| 5.3 | Implement cold-start session gate — route to auth graph or shell graph with no splash flash | 2 | $50 |
| 5.4 | Implement premium gate — rising-edge counter, back-stack-aware latch, re-evaluation on resume | 2 | $50 |
| 5.5 | Implement Play entitlement reconciliation and Android 13+ notification permission request | 2 | $50 |
| 5.6 | Implement deep-link scheme `kornerz://` with cold-start and warm-start resolution | 2 | $50 |
| 5.7 | Implement deep-link resolver — burst-link queueing, failure fallback to Home, diagnostics | 2 | $50 |
| 5.8 | Implement bottom-bar visibility rules and cross-tab navigation via live `NavHostController` holders | 1 | $25 |
| | **Area 5 subtotal** | **16** | **$400** |

---

### Area 6 — Home Tab

| # | Task | Hours | Cost |
|---|---|---:|---:|
| 6.1 | Build Home screen shell, scroll container and section composition | 2 | $50 |
| 6.2 | Build Home header — avatar, greeting, live points chip from gamification | 2 | $50 |
| 6.3 | Build Home sections — Loop of the Day, Recent Articles, Recent Loops | 2 | $50 |
| 6.4 | Build Home sections — Upcoming Nooks, Your Kornerz, Kornerz carousel | 2 | $50 |
| 6.5 | Integrate Coil image loading with shimmer placeholders and tap-through navigation | 2 | $50 |
| 6.6 | Implement Home repository and view-model state/events wiring | 3 | $75 |
| | **Area 6 subtotal** | **13** | **$325** |

---

### Area 7 — Kornerz Tab & Korner Detail

| # | Task | Hours | Cost |
|---|---|---:|---:|
| 7.1 | Build Kornerz list screen — all / recent / following views | 2 | $50 |
| 7.2 | Build community card with per-community theming and accent palette | 2 | $50 |
| 7.3 | Build Korner detail header — cover image, theme colour band, metadata | 2 | $50 |
| 7.4 | Build Korner detail tab bar and content sections — About sheet, nooks, members, recent loops | 3 | $75 |
| 7.5 | Implement follow / unfollow community against the `Fellow` collection | 2 | $50 |
| 7.6 | Implement join-video-call CTA and the Korner detail refresh bus | 2 | $50 |
| | **Area 7 subtotal** | **13** | **$325** |

---

### Area 8 — Nooks Tab

| # | Task | Hours | Cost |
|---|---|---:|---:|
| 8.1 | Build Nooks list screen with Live / Upcoming toggle and nook card component | 2 | $50 |
| 8.2 | Build scheduled-nook card with countdown formatting | 1 | $25 |
| 8.3 | Build Nook detail screen — cover banner, metadata block, title block | 2 | $50 |
| 8.4 | Implement join-upcoming action, session-info integration and back-to-Korner row | 2 | $50 |
| | **Area 8 subtotal** | **7** | **$175** |

---

### Area 9 — Loops Tab — Feed, Detail & Video Playback

| # | Task | Hours | Cost |
|---|---|---:|---:|
| 9.1 | Build Loops feed screen with infinite pagination, filter chips and search | 3 | $75 |
| 9.2 | Build loop list item view and shared card chrome | 2 | $50 |
| 9.3 | Build Loop detail screen — accent-colour theming carried from the tapped row | 2 | $50 |
| 9.4 | Integrate Media3 ExoPlayer video playback in Compose — lifecycle-aware player | 3 | $75 |
| 9.5 | Implement view-count increment, loop mapper, cursor pagination and loop creation write path | 1 | $25 |
| | **Area 9 subtotal** | **11** | **$275** |

---

### Area 10 — Loops Tab — Comments, Replies & Likes

| # | Task | Hours | Cost |
|---|---|---:|---:|
| 10.1 | Build create-comment screen — write, submit and optimistic state | 2 | $50 |
| 10.2 | Build comment list view with counts and timestamps | 2 | $50 |
| 10.3 | Build reply-thread screen with nested replies and live refresh | 2 | $50 |
| 10.4 | Implement loop like / unlike / check and comment like with batched like counts | 2 | $50 |
| 10.5 | Build add-comment bar and wire comment/reply navigation with typed route arguments | 2 | $50 |
| | **Area 10 subtotal** | **10** | **$250** |

---

### Area 11 — Profile, Account & Notifications

| # | Task | Hours | Cost |
|---|---|---:|---:|
| 11.1 | Build own profile screen — header, avatar, name, nickname, points badge, app-version footer | 2 | $50 |
| 11.2 | Build profile menu rows — Notifications, My Data, My Communities, Privacy Policy, Logout, Delete Account | 2 | $50 |
| 11.3 | Build edit-basic-info screen with 5 editable fields and per-field change dialogs | 3 | $75 |
| 11.4 | Build edit-success confirmation screen and popup | 1 | $25 |
| 11.5 | Build privacy policy PDF viewer for the profile entry point | 2 | $50 |
| 11.6 | Build account-deletion dialog and implement deletion via the `deleteUserAccount` Cloud Function | 2 | $50 |
| 11.7 | Implement profile repository and 4 supporting network APIs | 2 | $50 |
| 11.8 | Build in-app notification feed — paginated, unread presentation | 1 | $25 |
| | **Area 11 subtotal** | **15** | **$375** |

---

### Area 12 — Create Community, Admin & Stats

| # | Task | Hours | Cost |
|---|---|---:|---:|
| 12.1 | Build admin owned-communities list screen and community item row | 2 | $50 |
| 12.2 | Build community detail screen with stats dashboard layout and stat tiles | 2 | $50 |
| 12.3 | Implement parallel stats aggregation — members, notifications, events, articles, videos, forums, pending event requests | 3 | $75 |
| 12.4 | Build create-forum wizard shell with 5-step navigation and staged progress | 2 | $50 |
| 12.5 | Build wizard steps — title, rich-text description, cover image, video, publish | 3 | $75 |
| 12.6 | Implement staged upload pipeline — signed URLs, 0–40% video / 40–80% image progress, parallel thumbnail | 2 | $50 |
| | **Area 12 subtotal** | **14** | **$350** |

---

### Area 13 — Premium Subscription & Google Play Billing

| # | Task | Hours | Cost |
|---|---|---:|---:|
| 13.1 | Integrate Google Play Billing 8.0.0 — `BillingClientApi` wrapper and product queries | 3 | $75 |
| 13.2 | Implement purchase flow, purchase-result handling and restore purchases | 2 | $50 |
| 13.3 | Build subscription plans screen — Monthly and Yearly tiers with plan card and per-month-equivalent pricing | 2 | $50 |
| 13.4 | Implement trial eligibility from Play's own pricing phases | 1 | $25 |
| 13.5 | Implement server-side purchase verification via `verifySubscription` with graceful degradation | 2 | $50 |
| 13.6 | Implement entitlement persistence — expiry date, subscription type, platform marker | 1 | $25 |
| 13.7 | Build purchase success screen and wrong-account sheet for restores | 1 | $25 |
| 13.8 | Implement offline product catalog and `dev`/`prod` flavour-split billing bindings | 2 | $50 |
| | **Area 13 subtotal** | **14** | **$350** |

---

### Area 14 — Video Calling

| # | Task | Hours | Cost |
|---|---|---:|---:|
| 14.1 | Integrate Vonage / OpenTok Android SDK 2.34.0 — session, publisher, subscriber lifecycle | 3 | $75 |
| 14.2 | Build video call room screen, publisher tile and subscriber grid | 2 | $50 |
| 14.3 | Build call controls — mute, camera toggle, circular call buttons, leave | 2 | $50 |
| 14.4 | Build loading state and edge-to-edge coral backdrop with custom insets | 1 | $25 |
| 14.5 | Implement connection counts via `getSessionsInfo` and participant-joined notification | 1 | $25 |
| 14.6 | Handle camera and audio permissions, Bluetooth connect, audio focus and wake lock | 2 | $50 |
| | **Area 14 subtotal** | **11** | **$275** |

---

### Area 15 — Ranking, Push Notifications & Polish

| # | Task | Hours | Cost |
|---|---|---:|---:|
| 15.1 | Build Klouders leaderboard screen — monthly points aggregation and descending sort | 2 | $50 |
| 15.2 | Implement medal tiers and nickname resolution via batched `Users.whereIn("codeUser")` | 2 | $50 |
| 15.3 | Implement FCM push registration — `FirebaseMessagingService`, notification channel, foreground display with deep-link `PendingIntent`s | 2 | $50 |
| 15.4 | Implement gamification points engine — monthly window, points chip, add-points write path | 1 | $25 |
| 15.5 | Implement accessibility pass — TalkBack labels, content descriptions, font-scale support | 2 | $50 |
| 15.6 | Implement empty/error/retry state pass and final UI polish with animation refinement | 2 | $50 |
| | **Area 15 subtotal** | **11** | **$275** |

---

## Summary by Area

| # | Area | Hours | Cost |
|---|---|---:|---:|
| 1 | Project Foundation, Architecture & Build System | 21 | $525 |
| 2 | Design System & Shared UI Components | 24 | $600 |
| 3 | Data Layer, Models & Backend Integration | 40 | $1,000 |
| 4 | Authentication & Onboarding | 25 | $625 |
| 5 | App Shell, Navigation & Deep Linking | 16 | $400 |
| 6 | Home Tab | 13 | $325 |
| 7 | Kornerz Tab & Korner Detail | 13 | $325 |
| 8 | Nooks Tab | 7 | $175 |
| 9 | Loops Tab — Feed, Detail & Video | 11 | $275 |
| 10 | Loops Tab — Comments, Replies & Likes | 10 | $250 |
| 11 | Profile, Account & Notifications | 15 | $375 |
| 12 | Create Community, Admin & Stats | 14 | $350 |
| 13 | Premium Subscription & Google Play Billing | 14 | $350 |
| 14 | Video Calling | 11 | $275 |
| 15 | Ranking, Push Notifications & Polish | 11 | $275 |
| | **TOTAL** | **245** | **$6,125** |

---

## Summary by Feature Area

| Feature Area | Hours | Cost | % of Total |
|---|---:|---:|---:|
| Onboarding & Authentication | 25 | $625 | 10.2% |
| Home Tab | 13 | $325 | 5.3% |
| Kornerz Tab & Korner Detail | 13 | $325 | 5.3% |
| Nooks Tab | 7 | $175 | 2.9% |
| Loops Tab (feed, video, comments, replies, likes) | 21 | $525 | 8.6% |
| Profile, Account Management & Notifications | 15 | $375 | 6.1% |
| Create Community, Admin & Stats | 14 | $350 | 5.7% |
| Premium Subscription & In-App Purchases | 14 | $350 | 5.7% |
| Video Calling | 11 | $275 | 4.5% |
| Ranking & Leaderboard | 4 | $100 | 1.6% |
| Push Notifications | 2 | $50 | 0.8% |
| Gamification & Points Engine | 1 | $25 | 0.4% |
| App Shell, Navigation & Deep Linking | 16 | $400 | 6.5% |
| Design System & Shared Components | 24 | $600 | 9.8% |
| Data Layer, Models & Backend Integration | 40 | $1,000 | 16.3% |
| Project Foundation, Build & CI | 21 | $525 | 8.6% |
| Accessibility, States & UI Polish | 4 | $100 | 1.6% |
| **TOTAL** | **245** | **$6,125** | **100%** |

---

## Invoice Totals

| | |
|---|---:|
| **Total hours worked** | **245** |
| **Rate** | **USD 25.00 / hour** |
| **Subtotal** | **$6,125.00** |
| Tax (if applicable) | _$0.00_ |
| **TOTAL DUE** | **$6,125.00 USD** |

---

## Deliverables Included

- Complete native Android source — 18 Gradle modules (4 core, 13 feature, 1 app)
- **77 production screens** across 15 functional areas
- Design system — 46 brand colours, 20 community gradients, Satoshi typography, shape and spacing tokens, light + dark themes
- **34 reusable shared UI components**
- **38 data-model classes**, 20+ Firestore collections, 5 Cloud Functions integrated
- 16 repository classes — `Flow`-based streaming and `Result`-based one-shot operations
- **50 documented capability specifications** (~318 requirements, ~800 scenarios)
- CI pipeline with build gate and APK artifact upload
- Technical documentation set — architecture, phase plan, screen inventory, project brief
- Architected on the shared Firebase backend so Android and iOS run on one user base and one content graph

---

## Third-Party Services

Integrated into the application. **Licence, account and usage fees are billed directly by the providers and are not included in this invoice.**

| Service | Version | Purpose |
|---|---|---|
| Google Play Billing | 8.0.0 | Subscription purchases and entitlement |
| Vonage / OpenTok Android SDK | 2.34.0 | Multi-party video calling |
| Firebase (Firestore, Functions, Storage, Messaging, Config, Crashlytics) | BOM 33.5.1 | Backend, push, storage, crash reporting |
| Media3 ExoPlayer | 1.4.1 | Video playback |
| Coil | 2.7.0 | Image loading |
| Hilt | 2.52 | Dependency injection |
| Navigation-Compose | 2.8.4 | Navigation |
| OkHttp | 4.12.0 | HTTP and WebSocket |
| DataStore Preferences | 1.1.1 | Session persistence |
| Jetpack Compose (Material 3) | BOM 2024.10.01 | UI toolkit |
| Kotlin | 2.0.21 | Language runtime |
| Android Gradle Plugin / JDK | 8.8.0 / 17 | Build toolchain |

---

## Terms & Conditions

1. **Payment terms:** Net 14 days from invoice date.
2. **Billing basis:** Actual engineering hours applied by a single senior Android developer at USD 25.00 per hour. Every task above is capped at 8 hours.
3. **Scope changes:** Requests outside the delivered scope — including the 123 legacy iOS screens excluded by design, additional languages beyond English, authoring an automated test suite, Play Store listing management, and custody of the release-signing key — are quoted separately.
4. **Excluded third-party costs:** Google Play service fees, Google Cloud / Firebase infrastructure charges, Vonage account and usage fees, and any app-store distribution fees.
5. **Backend dependency:** The application targets the shared Firebase project `kornerz-72fba`. Continued operation requires its Cloud Functions and Firestore collections to remain deployed and available.
6. **Warranty:** Defect remediation against the delivered specification is provided at no charge within 30 days of invoice date.
7. **Ownership:** On full settlement of this invoice, the client receives full ownership of the delivered Android source code, subject to the terms of any pre-existing client agreement.
