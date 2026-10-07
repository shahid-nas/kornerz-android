# Kornerz Android — Project Brief

**Status:** Draft v0.1 (exploration)
**Owner:** Android team
**Date:** 2026-10-07
**Source of truth:** `kornerz/` (iOS app) — KornerzUniversal workspace

---

## 1. Vision

Ship a Kornerz Android client with **functional parity** to the existing iOS app, so the same users, content, communities, and subscriptions work seamlessly across both platforms. One user base, one content graph, two clients.

---

## 2. Goals & Non-Goals

### Goals
- Same feature surface as iOS (auth, communities, loops/forums, events, articles, videos, video calls, notifications, ranking, premium).
- Share Firebase backend with iOS — no parallel data store.
- Premium subscription works cross-platform with platform-aware tracking.
- Match the iOS visual language (colors, typography, spacing).

### Non-Goals (v1)
- Sharing code with iOS via Kotlin Multiplatform (separate codebase).
- Replacing the video-calling provider used by iOS (Android will use the same provider; if the provider is replaced, both apps move together).
- Web client.
- Any backend Cloud Function rewrite for Android — existing endpoints (`oneventcreation`, `onarticlecreation`, `videocallstarted`, `onforumcreation`, `sendrecoveryemail`, `oncomment`, `getusercountsinasession`, `getSessionsInfo`, `issueUploadUrl`) are reused.

---

## 3. Feature Scope

Mapped directly from iOS source. Each row is a screen/feature group with the iOS reference module(s).

### 3.1 Onboarding & Auth
| Feature | iOS source |
|---|---|
| Splash | `SwiftUI/OnBoarding/Splash/` |
| Phone + OTP login | `User/LoginVC/`, `User/ConfirmCode/` |
| Email signup | `User/LoginVC/`, `SwiftUI/OnBoarding/Signup/` |
| Country code selector | `Models/CountryCodes.swift`, `CountryCodesJson/` |
| Forgot password | `User/ForgotPassword/` |
| Change password | `User/ChangePassword/` |
| Terms acceptance | `User.acceptedTermsCondition` |
| Force-update gate | `SwiftUI/ForceUpdate/` |

### 3.2 Home (Bottom Tabs)
The iOS tab bar (AppTabController.swift:28-32) maps 1:1:
1. **Home** — `SwiftUI/Dashboard/HomeTab/`
2. **Kornerz** — `SwiftUI/Dashboard/Korner/KornerTab/`
3. **Nooks** — `SwiftUI/Dashboard/Nooks/NookTab/`
4. **Loops** — `SwiftUI/Dashboard/Loops/LoopsTab/`
5. **Klouders (ranking)** — `HomeVC/Ranking/`

Each tab has:
- Listing view (`*ListScreen.swift`, `*ViewController.swift`, `*ViewModel.swift`)
- Detail / detail-as-admin view
- Filters, pagination, search

### 3.3 Communities
| Feature | iOS source |
|---|---|
| List + join | `HomeVC/CommunitiesVC/CommunitiesVC.swift` |
| Community detail | `HomeVC/CommunitiesVC/CommunityDetailVC/` |
| Community detail (admin) | `HomeVC/CommunityDetailAsAdmin/` |
| Create community (wizard) | `HomeVC/CreateCommunity/` — 4 steps (theme → name → description → confirm) |
| Member list | `HomeVC/CommunitiesVC/UserJoinedCommunityVC/` |

### 3.4 Content — Posts / Loops / Forums
The iOS app is mid-migration: legacy `Post` model → new `Loop` model, with `Forum` as a separate forum/discussion entity. Android should implement the **Loop + Forum** surface; `Post` is deprecated on the iOS side per `Backend/AutoStartServices/PostToLoopMigration.swift`.

| Feature | iOS source |
|---|---|
| Loops feed | `HomeVC/PostsList/`, `SwiftUI/Dashboard/Loops/` |
| Loop detail + comments | `SwiftUI/Dashboard/Loops/LoopDetail/`, `LoopCommentRepository.swift` |
| Loop likes | `LoopLikeRepository.swift` |
| Forums feed | `HomeVC/ForumsList/` |
| Forum detail | `HomeVC/ForumDetail/` |
| Comments | `HomeVC/ForumDetail/CommentView.swift` |

### 3.5 Content Creation Wizards (multi-step)
| Wizard | iOS source | Steps |
|---|---|---|
| Article | `HomeVC/ArticleCreation/` | cover image → cover/title → description → preview |
| Event | `HomeVC/EventCreation/` | name → description → date/time → seats → speakers → guests → preview |
| Video | `HomeVC/VideoCreation/` | select video → cover → description → preview |
| Forum | `HomeVC/AddForum/` | title → description → video → cover → publish |

### 3.6 Video Calls (OpenTok / Vonage)
| Feature | iOS source |
|---|---|
| Community meeting room | `HomeVC/OpenTokMeeting/CommunityMeetingVC/` |
| Publisher / subscriber containers | `SwiftUI/VideoCall/` |
| Session token via WebSocket | `Backend/Services/OpenTokTokenService.swift`, `Backend/Services/WebSocketService.swift` |
| Connection count | `ApiService.getConnectionCounts` → Cloud Function `getusercountsinasession` |
| Screen recording | `ScreenRecorder.swift`, `RecordedScreen.swift` (admin-only) |

### 3.7 Profile & Account
| Feature | iOS source |
|---|---|
| User profile (own + others) | `HomeVC/ProfileVC/UserProfileVC.swift` |
| Edit basic info | `HomeVC/ProfileVC/EditBasicInfoVC/` |
| Privacy policy | `HomeVC/ProfileVC/PrivacyPolicyVC/` |
| Account deletion | posts `.userDeletedHisAccount` |
| Push token registration | `AppDelegate.swift` `MessagingDelegate` |

### 3.8 Notifications
| Feature | iOS source |
|---|---|
| In-app notification list | `HomeVC/Notifications/` (ViewModel + Screen + Controller), `HomeVC/NotificationVC/` (legacy) |
| Push notifications | `Firebase/Messaging` via `AppDelegate` |
| Deep-link handling | `DeepLinks/DeepLinksManager.swift`, `AppDelegate` extension |

### 3.9 Gamification & Ranking
| Feature | iOS source |
|---|---|
| Ranking screen | `HomeVC/Ranking/RankingScreen.swift` |
| Gamification data model | `Models/Gamification.swift` |
| Firestore gamification service | `FirestoreService/GamificationService.swift` |

### 3.10 Premium / Subscription
| Feature | iOS source |
|---|---|
| IAP manager | `InAppPurchase/InAppManager.swift` |
| Subscription screen | `InAppPurchase/SubscriptionController.swift`, `SubscriptionScreen.swift` |
| StoreKit config | `InAppPurchase/Kornerz-SKConfig.storekit` |
| User premium flag | `User.isUserPremium`, `subscriptionExpiryDate`, `subscriptionType`, `subscriptionPlatform` |
| Auto-gating | `AppTabController.showSubscriptionScreen()` |

---

## 4. Architecture

### 4.1 Recommended Android stack
- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3) primary; classic Views only where a vendored SDK forces it (likely the video-call surface).
- **Min SDK / target SDK:** `minSdk 26` (Android 8), `targetSdk` = latest stable at kickoff (§11.6).
- **Architecture:** MVVM + Repository, mirroring `kornerz/Kornerz/Backend/Repositories/`.
- **Navigation:** Jetpack `Navigation-Compose`, or a coordinator library if we want to mirror the iOS coordinator pattern more literally.
- **DI:** Hilt.
- **Async:** Coroutines + Flow / `StateFlow`.

### 4.2 Module sketch (proposed)
```
app/                          ← application module
core/
  model/                      ← Kotlin data classes mirroring Models/*.swift
  network/                    ← Retrofit / Ktor client + Firebase Functions adapters
  storage/                     ← Firebase Storage wrapper (mirrors FirebaseStorageService.swift)
  ui/                         ← design tokens (colors, fonts), shared Compose components
feature/
  auth/                       ← login, signup, OTP, forgot-password
  home/                       ← Home tab + dashboard
  kornerz/                    ← Korner tab
  nooks/                      ← Nooks (communities) tab
  loops/                      ← Loops tab + forum tabs
  ranking/                    ← Klouders tab
  community/                  ← community detail, create-community wizard
  content/                    ← article / event / video / forum creation wizards
  videocall/                  ← OpenTok/Vonage integration
  notifications/
  profile/
  subscription/               ← Google Play Billing
```

### 4.3 Firebase wiring mirrors iOS
- **Same Firebase project** as iOS (`kornerz-72fba.appspot.com`).
- **Firestore collections** (used directly by iOS — Android will read/write the same ones): `Communities`, `CommunityAdm`, `Events`, `Forum`, `Videos`, `Users`, plus notification/loop/comment collections.
- **Auth:** iOS does **not** use FirebaseAuth for end users (`FIREBASE_STORAGE_MIGRATION.md` calls this out explicitly). Login is via custom Cloud Functions over HTTPS, returning a `User` payload mirrored to Firestore. Android re-implements the same custom-auth flow.
- **FCM:** `FirebaseMessaging` for push, with the same topics/segmentation strategy.
- **Storage:** Firebase Cloud Storage via v4 signed URLs issued by `issueUploadUrl` Cloud Function — same path conventions (`loops/{userCode}/{id}/…`, etc.).

### 4.4 Video calling
**Decision: keep the same provider as iOS.** Concrete actions on kickoff:
- Confirm Vonage Android SDK availability and licensing (the iOS app uses `OTXCFramework 2.33.0`).
- Re-implement the WebSocket session-token handshake (`OpenTokTokenService.swift` ↔ Android equivalent) against the same token-issuing service.
- Connection-count polling (`getusercountsinasession`) and session-info (`getSessionsInfo`) endpoints are reused.

---

## 5. Shared Backend Surface (what Android calls)

Identified from `ApiService.swift`, `Helper/`, and Cloud Function references:

| Endpoint (CF) | iOS method | Android usage |
|---|---|---|
| `oneventcreation` | `notifyEventCreated` | trigger push to community members when event created |
| `onarticlecreation` | `notifyArticleCreated` | same for articles |
| `videocallstarted` | `notifyVideoCallJoined` | notify when someone joins a video room |
| `onforumcreation` | `notifyForumCreated` | notify when a forum is created |
| `sendrecoveryemail` | `sendRecoveryEmail` | password recovery |
| `oncomment` | `notifyCommentCreated` | notify on comment |
| `getusercountsinasession` | `getConnectionCounts` | live video call counts |
| `getSessionsInfo` | `getSessionsInfo` | bulk session lookup |

---

## 7. Visual Design

### 7.1 Colors
iOS uses literal hex-prefixed constants in `Helper/Config.swift` (`C5D47ED`, `C353446`, etc.). Mirror as design tokens in `core/ui/` — these map 1:1 with hex values (e.g., `C5D47ED → #5D47ED`).

### 7.2 Typography
iOS uses the **Satoshi** font family (`.otf` files in `kornerz/Kornerz/Fonts/`). Bundle the same `.otf` files in Android `res/font/` and expose through Compose `FontFamily`.

### 7.3 Spacing / shape
iOS has no formal design-token system; components use ad-hoc insets. For Android we will codify a minimal set in `core/ui/Tokens.kt` (8pt grid, 4/8/12/16/24).

---

## 8. Localization

iOS supports **English** and **Portuguese (Brazil)** via `Localizable.xcstrings`. **Android launches English-only** (§11.7); ships `values/strings.xml` only. Portuguese-BR will be added later as `values-pt-rBR/strings.xml` without retrofit.

---

## 9. Platform-Specific Behavior

| Concern | iOS approach | Android approach |
|---|---|---|
| Push | APNs via FCM | FCM directly |
| Subscription | StoreKit + `StoreKitConfiguration.storekit` | Google Play Billing v6+; RTDN to Cloud Function updates `subscriptionPlatform = "Android"` |
| OTP text input | `AEOTPTextField` | Material `OtpTextField` equivalent (custom Compose or library) |
| Keyboard avoidance | `IQKeyboardManager` | `WindowInsets.ime` in Compose |
| Animations | `lottie-ios` | `lottie-android` (same JSON files) |
| WebSocket | `Starscream` | OkHttp WebSocket |
| Image loading | `KFImage` (Kingfisher — referenced but commented out in Podfile) | Coil for Compose |
| Navigation | `FlowStacks` (coordinator pattern) | `androidx.navigation:navigation-compose` |

---

## 10. Non-Functional Requirements

- **Cold-start to first frame:** < 2 s on a mid-range device.
- **App size budget:** ≤ 60 MB (iOS reference is in this range with all assets).
- **Offline behavior:** cached reads for the home/loops/nooks feeds; queued writes for likes/comments when possible.
- **Crash-free sessions:** ≥ 99.5%.
- **Accessibility:** content descriptions on all images, talkback-friendly focus order, dynamic-type / font-scale respect.

---

## 11. Open Decisions

All decisions resolved.

| # | Decision | Status | Choice | Rationale |
|---|---|---|---|---|
| 1 | UI paradigm | ✅ **Resolved** | **Compose-first, with `AndroidView` wrappers only where vendored SDKs force it (Vonage, rich-text)** | iOS app is migrating toward SwiftUI; Android should align directionally. Greenfield — no legacy to migrate. Vonage publishes first-party Jetpack Compose samples (8, including BasicVideoChat / Multiparty / ScreenSharing). SDK exposes `Publisher.view` / `Subscriber.view` as Android `View`s, wrapped in Compose via `AndroidView`. Mirrors how iOS SwiftUI wraps the same SDK via `UIViewRepresentable` (`PublisherContainer.swift`, `SubscriberContainer.swift`). Forced-Views footprint is small (~2 components). |
| 2 | Video-calling provider | ✅ **Resolved** | **Vonage (OpenTok)** — same provider as iOS | iOS uses `OTXCFramework 2.33.0`; Android SDK `2.34.0` on Maven Central, actively maintained, official Compose samples. WebSocket session-token handshake reimplemented against the same token-issuing service (`OpenTokTokenService.swift` ↔ Android equivalent). Same provider on both platforms avoids protocol-level divergence. |
| 3 | Backend sharing | ✅ **Resolved** | **Shared Firebase project (`kornerz-72fba`)** — same Firestore collections, same Cloud Functions, one user base | Single source of truth for users, content, subscriptions. iOS does not use FirebaseAuth for end users; Android re-implements the same custom-auth Cloud Function flow. |
| 4 | Phase 1 scope | ✅ **Resolved** | **Full feature parity** with iOS at launch | All 26 model types, 5 tabs, 5 creation wizards, video calls, gamification, IAP, deep links, force-update gate. iOS surface is large but achievable; phasing deferred to milestone breakdown (see §14). |
| 5 | Code sharing | ✅ **Resolved** | **Separate Android codebase, no KMP** | Kotlin Multiplatform would add toolchain complexity without proportional benefit; full rewrite in Kotlin with explicit model translations. Drift is acceptable trade for velocity. |
| 6 | Min SDK | ✅ **Resolved** | **`minSdk 26` (Android 8.0), `targetSdk` = latest stable at kickoff** | Vonage requires 24+; `26` matches iOS 16's effective device coverage and avoids legacy notification-channel edge cases. |
| 7 | Languages at launch | ✅ **Resolved** | **English only at launch** | Defers pt-BR localization work; `values/strings.xml` is the only file needed at launch. pt-BR can be added later without retrofitting (`values-pt-rBR/strings.xml`). |

---

## 12. Risks

- **Video-calling SDK drift** between iOS and Android providers if not chosen deliberately. *Mitigated:* same provider on both platforms.
- **Schema drift** in Firestore if iOS ships a model change Android hasn't mirrored yet.
- **Localization drift** — English-only at launch removes the immediate risk; process TBD if pt-BR is added later.
- **Subscription reconciliation** — Google Play RTDN can be delayed; need graceful fall-back for users who purchase but the server doesn't see it for minutes.
- **Push notification parity** — payload shape, deep-link routing, and cold-start behavior must stay in lockstep with iOS.

---

## 13. Suggested Milestones (full parity)

Full parity (§11.4) doesn't mean "everything ships on day one." It means the *target* is full parity. Suggested sequencing — each milestone is independently shippable:

| M | Milestone | Notes |
|---|---|---|
| **M0** | Foundation | Project skeleton, Firebase wiring, design tokens, theme, navigation, custom font (Satoshi), DI (Hilt), base modules. |
| **M1** | Auth & onboarding | Splash, phone+OTP, email signup, country codes, forgot/change password, force-update gate. Mirrors `SwiftUI/OnBoarding/`. |
| **M2** | Tab shell + Home | Lists Home + Korner + Nooks + Loops + Klouders (Klouders) tabs with feed + detail skeletons. No creation yet. |
| **M3** | Communities + content | Community detail (member + admin views), CreateCommunity wizard, member listing. |
| **M4** | Loops & forums | Loop feed, loop detail with comments, forum feed, forum detail. Replaces legacy Post surface. |
| **M5** | Creation wizards | Article, Event (multi-step), Video, Forum wizards. |
| **M6** | Video calling (Vonage) | `feature/videocall/`, WebSocket session token (mirrors `OpenTokTokenService.swift`), publisher/subscriber Compose wrappers, screen recorder for admin. |
| **M7** | Notifications + deep links | FCM registration, in-app notifications screen, `DeepLinksManager` equivalent, cold-start deep-link routing. |
| **M8** | Premium / IAP | Google Play Billing v6+, RTDN → Cloud Function updates `User.subscriptionPlatform = "Android"`. Mirrors `AppTabController.showSubscriptionScreen()` gating. |
| **M9** | Gamification + admin | Ranking screen, gamification data model, admin-only views (CommunityDetailAsAdmin). |
| **M10** | Profile, polish, perf | User profile, edit basic info, privacy policy, account deletion. Crash-free-session SLO, accessibility pass, offline reads. |

Milestones are **sequenced, not committed** — subject to re-ordering once proposal is opened.

---

## 14. References (iOS)

- `kornerz/Podfile` — dependency manifest
- `kornerz/Podfile.lock` — pinned versions
- `kornerz/Kornerz/Helper/Config.swift` — colors, fonts, IPFS/Firebase storage config
- `kornerz/Kornerz/Helper/AppInfo.swift` — app/device metadata
- `kornerz/Kornerz/Helper/ApiService.swift` — Cloud Function endpoints
- `kornerz/Kornerz/App/AppDelegate.swift` — push, deep links, Firebase init
- `kornerz/Kornerz/Models/*.swift` — domain model (26 files)
- `kornerz/Kornerz/Backend/Repositories/*.swift` — repository contracts
- `kornerz/Kornerz/Backend/Services/*.swift` — services
- `kornerz/Kornerz/SwiftUI/TabView/AppTabController.swift` — tab structure
- `kornerz/Kornerz/LocalizationStringFiles/Localizable.xcstrings` — i18n source
- `kornerz/FIREBASE_STORAGE_MIGRATION.md` — current storage migration status
- `kornerz/Kornerz/Fonts/*.otf` — Satoshi font family
- `github.com/Vonage/vonage-video-android-sdk-samples/JetpackCompose/` — reference Vonage Compose samples