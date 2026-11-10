# AGENTS.md — KornerzUniversal

AI onboarding and working rules for this repository. Read this before changing code.

## 1. What this project is

**KornerzUniversal** is a workspace holding the Kornerz social-community product across two clients:

- **`kornerz/`** — the original **iOS app** (Swift / UIKit + SwiftUI, CocoaPods). It is the **source of truth** for product behavior, data model, and visual language.
- **`KornerzAndroid/`** — a native **Android app** (Kotlin, Jetpack Compose) built to reach functional parity with iOS, sharing the **same Firebase backend** (`kornerz-72fba`). It is the active development target.

- **Product:** social platform with communities ("Kornerz"), nooks, loops/forums, video calls, gamification, notifications, and premium subscriptions.
- **Language / UI:** Kotlin 2.0 + Jetpack Compose (Material 3) on Android; Swift 5 / UIKit + SwiftUI on iOS.
- **Deployment targets:** Android `minSdk 26`, `compileSdk 35`, `targetSdk 35`. iOS targets are defined in `kornerz/Kornerz.xcodeproj`.
- **Backend:** shared Firebase project — Firestore, Cloud Functions, Cloud Storage, FCM. Android uses the **same** collections, Cloud Functions, and user base as iOS.
- **Product principles:** one user base, one content graph, two clients. Parity over novelty. Design tokens mirror the iOS palette and the Satoshi typeface.

Canonical documents:

| Document | Role |
|---|---|
| `docs/project-brief.md` | Vision, scope, resolved platform decisions, backend surface |
| `docs/architecture.md` | **Canonical** Android architecture, patterns, conventions (frozen by P0) |
| `docs/screen-inventory.md` | The 77 live screens and the iOS reference for each |
| `docs/phases.md` | Phased delivery plan (P0–P10) and per-phase exit checklists |
| `openspec/specs/` | Source of truth for shipped capability specs |
| `openspec/changes/` | In-flight and archived changes (archived under `changes/archive/`) |

Treat `docs/architecture.md` and the source tree as authoritative. When they disagree with older planning notes, they win.

## 2. Repository layout

```
KornerzUniversal/                 ← repository root
├── AGENTS.md
├── KornerzAndroid/                ← Android app (active target)
│   ├── settings.gradle.kts        ← module registry
│   ├── build.gradle.kts           ← plugin declarations (all `apply false`)
│   ├── gradle/libs.versions.toml   ← canonical version catalog
│   ├── gradle/wrapper/            ← Gradle 8.10.2
│   ├── app/                       ← application module (dev + prod flavors)
│   ├── core/
│   │   ├── model/                 ← pure Kotlin data classes
│   │   ├── network/               ← Firestore + Cloud Functions + FCM repositories
│   │   ├── storage/               ← DataStore session, interests, country cache
│   │   └── ui/                    ← design tokens + shared composables
│   └── feature/
│       ├── shell/                 ← MainActivity, 5-tab NavHost, deep links, premium gate
│       ├── auth/                  ← login, signup wizard, OTP, password reset
│       ├── home/                  ← Home tab
│       ├── kornerz/               ← Kornerz tab + Korner detail
│       ├── nooks/                 ← Nooks tab
│       ├── loops/                 ← Loops tab + comments + reply threads
│       ├── community/             ← create-community wizard, admin list
│       ├── profile/               ← profile, edit basic info, privacy policy PDF
│       ├── notifications/         ← in-app notifications list
│       ├── subscription/          ← Google Play Billing
│       ├── videocall/             ← Vonage video rooms
│       └── ranking/               ← Klouders leaderboard
├── kornerz/                       ← iOS app (source of truth for behavior)
│   ├── Kornerz/                   ← App, Backend, Components, HomeVC, Models, SwiftUI, User, …
│   ├── Kornerz.xcodeproj
│   ├── Kornerz.xcworkspace
│   ├── Pods/                      ← CocoaPods (gitignored)
│   └── Podfile / Podfile.lock
├── docs/                          ← brief, architecture, phases, screen inventory
├── design/                        ← Android icon generation script + assets
├── openspec/
│   ├── config.yaml
│   ├── specs/                     ← 50 shipped capability specs
│   └── changes/archive/           ← 15 completed changes
├── .github/workflows/android-pr.yml
├── .kilo/                         ← Kilo config and agent manager
└── .kilocode/skills/              ← openspec skills
```

Important source roots:

- Android app entry: `KornerzAndroid/app/src/main/kotlin/com/kornerz/kornerz/`
- Android shared UI/tokens: `KornerzAndroid/core/ui/src/main/kotlin/com/kornerz/core/ui/`
- Android models: `KornerzAndroid/core/model/src/main/kotlin/com/kornerz/core/model/`
- Android data layer: `KornerzAndroid/core/network/src/main/kotlin/com/kornerz/core/network/`
- iOS app: `kornerz/Kornerz/`

## 3. Build and run

Android (Gradle Kotlin DSL, JDK 17 required):

```sh
cd KornerzAndroid

# Dev debug APK (the phase gate artifact)
./gradlew :app:assembleDevDebug

# Install and launch on a connected device/emulator
./gradlew :app:installDevDebug

# Lint
./gradlew :app:lintDevDebug
```

iOS (source of truth; read-only for most tasks):

```sh
xcodebuild build -workspace kornerz/Kornerz.xcworkspace -scheme Kornerz -destination 'generic/platform=iOS'
```

- The Android build uses a **version catalog** at `KornerzAndroid/gradle/libs.versions.toml`. Every dependency and plugin version lives there — never inline a version in a module.
- `settings.gradle.kts` is the module registry; a new module must be included there and in `app/build.gradle.kts`.
- `KornerzAndroid/app/src/dev/google-services.json` and `.../prod/google-services.json` are flavor-specific Firebase configs.
- `docs/architecture.md` §3 is the **canonical library catalog**. Adding a dependency not listed there requires an OpenSpec amendment to that document first.
- There is no formatter config in the repo, but `docs/architecture.md` names Detekt and Ktlint as the intended static-analysis tools.
- `docs/architecture.md` §9 describes convention plugins under `KornerzAndroid/build-logic/`. That directory does not exist yet; each module currently declares its own plugins inline. Follow the actual repo state over the document when they disagree, and prefer creating the convention plugin when adding a new module.

### Verification policy — compile only

Unless the user explicitly asks otherwise, verification means implementing the requested change and compiling the affected target.

#### Do not

- Do not run unit, UI, or Gradle test tasks unless the user explicitly asks in that message. **The Android port carries no automated test suite** — no `src/test` or `src/androidTest` source set exists in any module, and no test-scoped dependency is in the version catalog.
- Do not write unit or UI tests unless the user explicitly asks in that message.
- Do not launch the app, boot an emulator, or drive the UI.
- Do not start background processes, watchers, or dev servers for verification.
- Do not run an extra "sanity check" command after a successful compile.
- Do not create OpenSpec proposals or artifacts unless the user explicitly asks.
- Do not modify unrelated repository content while making a scoped change.

#### Do

1. Implement the requested change.
2. Compile the affected module: `./gradlew :app:assembleDevDebug` (or the narrower `:feature:<x>:assembleDebug` when the change is module-local).
3. If compilation fails, fix the errors and recompile until it succeeds.
4. Report failures verbatim and do not repeat the same failing command without a change.
5. State that tests were not run and the app was not launched when that applies.

If runtime-only behavior must be observed (layout, deep-link routing, Play Billing, Vonage rooms), state exactly what the user needs to check manually instead of launching the app yourself.

This policy applies to subagents as well.

## 4. Architecture

### 4.1 Style — MVVM + Repository + Unidirectional Data Flow

**Mandatory** for every Android module and screen:

```
Composable screen (stateless) → ViewModel → Repository → Firestore / Cloud Functions / DataStore
```

- Screen composables render `UiState` and emit `UiEvent`. No business logic.
- ViewModels hold a `StateFlow<UiState>`, expose a `sealed interface UiEvent`, and orchestrate repository calls.
- Repositories expose `Flow<T>` for streams and `suspend fun ... : Result<T>` for one-shot actions. **No `LiveData`.**
- Navigation happens through `Channel<NavEvent>`; ViewModels never touch `NavController` directly.
- Loading/error states use the sealed `AsyncValue<T>` (`Loading` / `Success` / `Error`). **No `isLoading` booleans, no try/catch ladders in UI.**
- Cross-cutting: Hilt DI, Navigation-Compose, Coroutines/Flow.

### 4.2 Module boundaries

```
core/ui, core/network, core/storage, core/model  ← imported by feature/* and app
feature/*                                        ← imported by app only (feature:shell also by app only)
app                                              ← root module
```

- Enforced by Gradle `implementation` / `api` declarations.
- **No circular dependencies.**
- A `core/*` module must never import a `feature/*` module.
- `feature/shell` owns the tab graph and routes into features through route composables.

### 4.3 Layer boundaries inside a feature

```
feature/<name>/src/main/kotlin/com/kornerz/feature/<name>/
├── <Name>Screen.kt        ← @Composable entry (collects state, wires hiltViewModel())
├── <Name>Content.kt       ← stateless render
├── <Name>ViewModel.kt     ← @HiltViewModel
├── <Name>State.kt         ← data class
├── <Name>Event.kt         ← sealed interface
├── <Name>NavEvent.kt      ← navigation events (if any)
├── <Name>Repository.kt    ← repository contract
├── components/            ← screen-private composables
└── di/                    ← feature-scoped Hilt module (rare)
```

Repositories and Firebase wiring live in `core/network/`, not inside feature modules. Feature modules consume `core/network` repositories via DI.

### 4.4 State management

- Compose + `StateFlow` via `collectAsStateWithLifecycle()`.
- Persistent state (session, interests, cached country codes) via **DataStore Preferences** in `core/storage`.
- Firestore listeners for live data; Cloud Functions over HTTPS for auth and mutations.
- Dependencies are wired with **Hilt** only. No service locator, no hand-rolled singletons.

### 4.5 Auth model (important)

- iOS does **not** use FirebaseAuth for end users. Login/signup go through **custom Cloud Functions over HTTPS** returning a `User` payload.
- Android re-implements the same custom-auth flow (`core/network/auth/`).
- Session is persisted in DataStore; the session token is attached to requests via an OkHttp interceptor.

## 5. Adding or changing a feature

1. **Model** — add or extend a data class in `KornerzAndroid/core/model/`.
2. **Repository** — add or extend the domain repository in `KornerzAndroid/core/network/<domain>/`; expose `Flow`/`suspend` per §4.1.
3. **Dependency injection** — bind in `core/network/di/` (or a feature-scoped module) with Hilt.
4. **Feature module** — if new, create `feature/<name>/`, register it in `settings.gradle.kts` and `app/build.gradle.kts`, and apply the same plugin set the sibling modules use.
5. **UI** — write `State`, `Event`, `NavEvent`, `ViewModel`, `Screen`, `Content`, and `components/` following §4.3.
6. **Shared components** — reuse from `core/ui/components/`; do not re-implement `KornerzButton`, `KornerzTextField`, `KornerzAvatar`, `KornerzEmptyState`, `KornerzLoadingView`, `KornerzChip`, `KornerzOtpTextField`, etc.
7. **Routing** — wire the destination into the `NavHost` in `feature/shell`.
8. **Design tokens** — use `core/ui/theme/` (`Color.kt`, `Type.kt`, `Shape.kt`, `Space.kt`). No hardcoded hex values, spacings, or durations in feature code.
9. **iOS parity** — when behavior must match iOS, read the corresponding screen in `kornerz/Kornerz/` (see `docs/screen-inventory.md` for the mapping) and mirror it.
10. **OpenSpec** — if the change adds or alters a shipped capability, write an OpenSpec change and update `openspec/specs/`. Every spec must carry a `## References` section linking to `docs/architecture.md`.

Do not add tests unless the user explicitly asks.

## 6. Current Android feature inventory

| Module | Ships |
|---|---|
| `feature/shell` | MainActivity, 5-tab bottom nav (Home, Kornerz, Nooks, Loops, Klouders), deep-link routing (cold + warm), premium gate, Play entitlement reconciliation, notification permission request |
| `feature/auth` | Welcome/splash, login, 9-step signup wizard, forgot password, OTP confirm, change password, force-update sheet |
| `feature/home` | Header, loop-of-the-day, recent articles, recent loops, upcoming nooks, your Kornerz, Kornerz carousel |
| `feature/kornerz` | Kornerz list, Korner detail (header, about, current nook, nooks list, members) |
| `feature/nooks` | Nooks tab, nook card, scheduled nook |
| `feature/loops` | Loops feed, filter chips, loop detail, comments, reply threads, creation |
| `feature/community` | Create-community wizard, admin owned-communities list |
| `feature/profile` | Own profile, edit basic info, privacy policy PDF viewer |
| `feature/notifications` | In-app notifications list, push handling |
| `feature/subscription` | Subscription plans, Google Play Billing purchase + success, premium gating |
| `feature/videocall` | Vonage publisher/subscriber containers, session token handshake, connection counts |
| `feature/ranking` | Klouders leaderboard |

Backend Cloud Functions reused from iOS: `oneventcreation`, `onarticlecreation`, `videocallstarted`, `onforumcreation`, `sendrecoveryemail`, `oncomment`, `getusercountsinasession`, `getSessionsInfo`, `issueUploadUrl`.

Keep `docs/screen-inventory.md`, `docs/phases.md`, and the OpenSpec specs synchronized when the inventory changes.

## 7. Kotlin / Compose conventions

- One top-level class per file; file name = class name. Nested classes stay with their parent.
- **Max ~300 lines per file**; refactor when exceeded.
- Naming: `HomeScreen`, `HomeContent`, `HomeViewModel`, `HomeState`, `HomeEvent`, `LoopRepository`. Composables are PascalCase; functions/properties camelCase.
- Composable parameter order: `Modifier` is **always the last parameter**.
- Module-level constants: top-level `const val` or an `object` — no `companion object` constants.
- Imports grouped: stdlib → third-party → internal → blank line → static imports.
- 4-space indentation, Kotlin official style.

## 8. Design system

- Tokens live in `KornerzAndroid/core/ui/theme/`: `Color.kt`, `Type.kt`, `Shape.kt`, `Space.kt`, `Theme.kt`.
- The palette mirrors iOS `kornerz/Kornerz/Helper/Config.swift` hex constants; the typeface is **Satoshi**, bundled from `kornerz/Kornerz/Fonts/*.otf`.
- Shared components live in `KornerzAndroid/core/ui/components/`.
- Every screen ships light **and** dark `@Preview`s.
- `design/generate_android_icons.py` generates launcher icon assets.
- App icons and splash assets live under `KornerzAndroid/app/src/*/res/`.

## 9. Android conventions (hard rules)

1. No business logic in Composables. Mutations go through `onEvent` → ViewModel.
2. No `var` in ViewModels. `MutableStateFlow` is the only mutable state.
3. No magic numbers — spacing, colors, durations come from tokens.
4. No hand-rolled singletons — use Hilt `@Singleton`.
5. No `runCatching` in Composables — errors are `AsyncValue.Error`.
6. No `!!` force unwrap — use `requireNotNull` with a meaningful message.
7. ViewModels never reference `NavController` directly.
8. No god ViewModels — one per screen; cross-screen state goes through repositories.
9. No DI by service locator.
10. No dependency outside `docs/architecture.md` §3 without an OpenSpec amendment.

## 10. Localization

- **English only at launch.** `KornerzAndroid/app/src/main/res/values/strings.xml` is the only string file.
- Portuguese-BR is deferred; when added, it goes in `values-pt-rBR/strings.xml` with no retrofit.
- iOS source of truth for copy: `kornerz/Kornerz/LocalizationStringFiles/Localizable.xcstrings`.

## 11. Platform-specific behavior

| Concern | iOS | Android |
|---|---|---|
| Push | APNs via FCM | FCM directly |
| Subscription | StoreKit + `Kornerz-SKConfig.storekit` | Google Play Billing 7.0.0; RTDN → Cloud Function sets `subscriptionPlatform = "Android"` |
| OTP input | `AEOTPTextField` | `KornerzOtpTextField` in `core/ui/components/` |
| Video calling | Vonage / OpenTok iOS SDK (`OTXCFramework 2.33.0`) | Vonage Android SDK 2.34.0 (`com.opentok.android`) |
| WebSocket | Starscream | OkHttp WebSocket |
| Image loading | Kingfisher-style `KFImage` | Coil Compose |
| Video playback | AVKit | Media3 ExoPlayer |
| Animations | lottie-ios | lottie-android (same JSON assets) |
| Navigation | `FlowStacks` coordinators | Navigation-Compose + `Channel<NavEvent>` |
| PDF | PDFKit | `com.github.barteksc:AndroidPdfViewer` 3.1.0-beta.1 |

Both apps use the **same** Vonage provider. Do not diverge the protocol layer between platforms.

## 12. Build variants, CI, and release

- Product flavors: `dev` (`applicationIdSuffix .dev`, app name "Kornerz Dev") and `prod`; flavor dimension `env`.
- Build types: `debug` (no minification) and `release` (R8 + resource shrinking on, `proguard-rules.pro`, currently signed with the debug key — **not uploadable to Play**).
- CI: `.github/workflows/android-pr.yml` runs on pull requests touching `KornerzAndroid/**`. It sets up JDK 17, caches Gradle, runs `./gradlew :app:assembleDevDebug`, and uploads the APK artifact (14-day retention).
- Upload keys, Play Console track, and release signing are **not configured** — they are open questions in `docs/phases.md`.

## 13. OpenSpec workflow

```
openspec/
├── config.yaml                 ← schema: spec-driven (context block is empty)
├── specs/<capability>/spec.md  ← source of truth for shipped behavior
└── changes/
    ├── <change-name>/          ← proposal.md, design.md, tasks.md, specs/<capability>/spec.md
    └── archive/                ← 15 completed changes (P0–P10 + parity follow-ups)
```

- Use the `openspec-propose`, `openspec-apply-change`, `openspec-explore`, and `openspec-archive-change` skills.
- Do not create OpenSpec proposals unless the user explicitly asks.
- Every spec MUST include a `## References` section linking to `docs/architecture.md`; a spec without it is non-compliant.

## 14. Code style

- Match the surrounding file. No configured formatter is enforced in CI today, but Ktlint/Detekt are the intended tools.
- Avoid drive-by refactors, renames, formatting sweeps, or unrelated cleanup.
- Preserve existing logging and comments unless the task requires changing them.
- Keep changes scoped to the user's request.

## 15. Gotchas

1. `docs/architecture.md` is canonical and frozen by P0; amendments require an OpenSpec change. It references `KornerzAndroid/build-logic/` convention plugins that **do not exist yet** — follow the real repo state.
2. `docs/phases.md` records the exit checklist per phase; the tag `phase-N-end` marks a launchable artifact.
3. The Android port intentionally has **no automated test suite**. Do not add test source sets without being asked.
4. Firestore collections are shared with iOS: `Communities`, `CommunityAdm`, `Events`, `Forum`, `Videos`, `Users`, plus loop/comment/notification collections. Schema drift between the clients is the main cross-platform risk.
5. `core/network` holds the repositories; feature modules must not call Firestore or Cloud Functions directly.
6. The 5-tab shell is Home, Kornerz, Nooks, Loops, Klouders (ranking).
7. iOS still contains ~123 dead screens that are excluded from the Android port; see `docs/screen-inventory.md`.
8. `kornerz/` is a nested Git repository with its own `.git` and is currently untracked in this repo.
9. `kornerz/Pods/` is gitignored; `Podfile.lock` is committed by convention.
10. `openspec/config.yaml` has an empty `context` block. Populate it only when the user explicitly asks for OpenSpec artifact generation.
11. `KornerzAndroid/local.properties` and all `build/` directories are gitignored.

## 16. Hard rules

### Do

- Keep the Android and iOS clients on the same Firebase backend, Firestore collections, and Cloud Functions.
- Follow the UDF contract in `docs/architecture.md` §2 for every screen.
- Respect module boundaries (`core/*` ← `feature/*` ← `app`).
- Use design tokens and shared components from `core/ui/` instead of ad-hoc values.
- Keep `Tool`-equivalent registries, specs, and documentation synchronized when the inventory changes.
- Keep changes scoped to the user's request.

### Do not

- Do not add analytics, telemetry, ads, or accounts beyond the existing model.
- Do not introduce a second backend or mock server — every phase uses the real shared Firebase project.
- Do not swap the video-calling provider on one platform only.
- Do not add an automated test suite, test dependencies, or `src/test` source sets unless explicitly asked.
- Do not add a dependency that is not in `docs/architecture.md` §3 without an OpenSpec amendment.
- Do not edit the iOS app unless the task is explicitly about iOS parity work.
- Do not edit unrelated documentation or source while completing a scoped task.
