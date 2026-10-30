# Kornerz Android — Architecture, Patterns & Conventions

**Status:** Canonical. Frozen by P0 (`openspec/changes/p0-android-foundation/`). Amendments require a new OpenSpec change.
**Scope:** Applies to **every** module and feature in the Android app. Inherited by P1, P2, …, P10 unless explicitly amended.
**Audience:** Every Android developer, every AI-assisted implementation agent.

> **All OpenSpec specs in this repository MUST include a `## References` section that links to this document.** A spec without this link is non-compliant.

---

## 1. High-level architecture

**Style: MVVM + Repository + Unidirectional Data Flow (UDF).**

```
┌─────────────────────────────────────────────────────────────────┐
│                          UI LAYER (Compose)                      │
│  Composable screens (stateless) — render UiState, emit UiEvent  │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  ViewModel layer (per screen)                            │   │
│  │  - holds ScreenState (StateFlow, immutable view)         │   │
│  │  - exposes ScreenEvent (sealed interface)                │   │
│  │  - orchestrates Repository calls                         │   │
│  │  - emits navigation via Channel<NavEvent>                │   │
│  └──────────────┬───────────────────────────────────────────┘   │
│                 │                                               │
│  ┌──────────────▼───────────────────────────────────────────┐   │
│  │  Repository layer (per domain)                           │   │
│  │  - Flow<T> for streams, suspend funcs for one-shots      │   │
│  │  - returns AsyncValue<T> or Result<T>                    │   │
│  │  - hides Firestore / REST / DataStore details │   │
│  └──────────────┬───────────────────────────────────────────┘   │
│                 │                                               │
│  ┌──────────────▼───────────────────────────────────────────┐   │
│  │  Data sources (Firestore, Cloud Functions, DataStore,    │   │
│  │  Firebase Storage, Vonage SDK, Play Billing)            │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  Cross-cutting: Hilt (DI) · Navigation-Compose · Coroutines     │
└─────────────────────────────────────────────────────────────────┘
```

Mirrors the iOS structure (`BaseCoordinator` → ViewController + ViewModel → Repository → Firestore) but adapted to Compose's reactive model.

---

## 2. Design patterns

### 2.1 Unidirectional Data Flow (UDF) — mandatory

Every screen follows this contract:

```kotlin
// ScreenState — what the UI renders (immutable)
data class HomeState(
    val header: AsyncValue<User> = AsyncValue.Loading,
    val loopOfDay: AsyncValue<Loop> = AsyncValue.Loading,
    val recentArticles: AsyncValue<List<Article>> = AsyncValue.Loading,
    // ...
)

// ScreenEvent — what the UI emits (user actions)
sealed interface HomeEvent {
    data object Refresh : HomeEvent
    data class LoopClicked(val loopId: String) : HomeEvent
}

// ViewModel
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.Refresh -> refresh()
            is HomeEvent.LoopClicked -> emitNav(NavEvent.NavigateToLoop(event.loopId))
        }
    }
}

// Screen — purely declarative
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(state = state, onEvent = viewModel::onEvent)
}

@Composable
fun HomeContent(state: HomeState, onEvent: (HomeEvent) -> Unit) {
    // Pure: given state + onEvent, render. No business logic.
}
```

**Why this contract:**
- ViewModels are testable without Compose (no Compose dependency).
- Composables are previewable with fake state.
- Survives configuration changes (StateFlow is lifecycle-aware).
- Single source of truth per screen.

### 2.2 `AsyncValue<T>` sealed interface — mandatory for loading/error

```kotlin
sealed interface AsyncValue<out T> {
    data object Loading : AsyncValue<Nothing>
    data class Success<T>(val data: T) : AsyncValue<T>
    data class Error(val throwable: Throwable) : AsyncValue<Nothing>
}
```

Every list/detail call exposes `AsyncValue<T>`. Composables render one of three layouts (`LoadingView` / `content` / `ErrorView`) by exhaustive `when`. **No** `isLoading = true` booleans. **No** try/catch ladders in the UI.

### 2.3 Repository return types

```kotlin
interface LoopRepository {
    // Stream — Firestore listener (continuous updates)
    fun observeLoops(filter: LoopFilter): Flow<List<Loop>>

    // One-shot action — REST call
    suspend fun likeLoop(loopId: String): Result<Unit>

    // Pagination
    suspend fun loadMore(cursor: String?): Result<PaginationResult<Loop>>
}
```

- **`Flow<T>`** for streams (Firestore listeners, push notifications, video call participants).
- **`suspend fun` returning `Result<T>`** for one-shot actions.
- **`suspend fun` returning `T` directly** when failure is impossible or already mapped.
- **No** `LiveData`. Compose uses `Flow` via `collectAsStateWithLifecycle()`.

### 2.4 Navigation via `Channel<NavEvent>` — mandatory

ViewModels never call `NavController` directly. Navigation is a side-channel:

```kotlin
class HomeViewModel : ViewModel() {
    private val _navEvents = Channel<NavEvent>(Channel.BUFFERED)
    val navEvents: Flow<NavEvent> = _navEvents.receiveAsFlow()
    
    private fun emitNav(event: NavEvent) { _navEvents.trySend(event) }
}

// Screen collects and routes
@Composable
fun HomeScreen(viewModel: HomeViewModel, navController: NavController) {
    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                is NavEvent.NavigateToLoop -> navController.navigate("loop/${event.loopId}")
            }
        }
    }
}
```

**Why:** keeps ViewModels Compose-agnostic and testable. iOS uses `Coordinator` for the same reason — this is the Compose equivalent.

### 2.5 Module boundaries = package boundaries

```
core/ui        ← imported by feature/*, app
core/network   ← imported by feature/*, app
core/storage   ← imported by feature/*, app
core/model     ← imported by feature/*, app
feature/shell  ← imported by app ONLY
feature/*      ← imported by app ONLY
app            ← root module
```

- Enforced by Gradle `implementation`/`api` declarations.
- The rules are enforced by the dependency graph itself plus code review — the project carries **no automated test suite**, so a boundary violation shows up as a compile error in `core/*` (it cannot see `feature/*`) rather than as a failing test.
- **No circular dependencies.** A new engineer cannot accidentally create a `feature/shell` → `feature/loops` import.

### 2.6 Hilt scopes

| Scope | Used for |
|---|---|
| `@Singleton` | Repositories, network clients, Firebase wrappers |
| `@ViewModelScoped` | Per-ViewModel helpers (rare — prefer passing via constructor) |
| ViewModel (Hilt-managed) | Per-screen state (via `hiltViewModel()`) |

**No** `@ActivityScoped` or `@FragmentScoped` unless we hit a real, documented use case. Default to `@Singleton` for data layer.

---

## 3. Library catalog (canonical list)

Versions are pinned in `KornerzAndroid/gradle/libs.versions.toml`. Do **not** introduce a library not listed here without an OpenSpec amendment to this document.

### Core

| Library | Purpose |
|---|---|
| Jetpack Compose BOM | UI version baseline |
| Compose Material 3 | Theme + components |
| Activity-Compose | `setContent { ... }` |
| Lifecycle Runtime Compose | `collectAsStateWithLifecycle()` |
| Lifecycle ViewModel Compose | `viewModel()`, `hiltViewModel()` |
| Lifecycle Process | `DefaultLifecycleObserver` for app-wide hooks |
| Navigation-Compose | NavHost + nested graphs |
| Hilt | DI (first-party Android) |
| Hilt Navigation Compose | `hiltViewModel()` inside composables |

### Async

| Library | Purpose |
|---|---|
| Kotlinx Coroutines | `Flow`, `StateFlow`, `viewModelScope` |
| Kotlinx Serialization | JSON for Cloud Function requests (replaces iOS `Codable`) |

### Data

| Library | Purpose |
|---|---|
| Firebase BoM | Pinned versions for Firebase Android libs |
| Firebase Auth KTX | Anonymous sign-in (P0 only) |
| Firebase Firestore KTX | Real-time listeners + queries |
| Firebase Storage KTX | v4 signed-URL uploads |
| Firebase Crashlytics KTX | Crash reporting |
| Firebase Cloud Messaging KTX | Push notifications (P6+) |
| DataStore Preferences | Session storage (replaces iOS `UserDefaults`) |
| OkHttp | WebSocket for Vonage (P9) |
| Vonage Android SDK | Video calling (P9) |
| Google Play Billing v6+ | Subscriptions (P8) |

### UI helpers

| Library | Purpose |
|---|---|
| Coil Compose | Image loading (replaces iOS `KFImage`) |
| Media3 ExoPlayer | Video playback (P9) |
| Lottie Compose | Lottie animations (replaces iOS `lottie-ios`) |
| Accompanist (SystemUiController, Insets, Permissions) | Edge-to-edge + permissions UI |

### Date / Time

| Library | Purpose |
|---|---|
| Kotlinx Datetime | Cross-SDK date math (replaces iOS `Date+Extension`) |

### Logging

| Library | Purpose |
|---|---|
| Timber | Tagged logging + Crashlyt integration |

### Testing

The Android port carries **no automated test suite**. No `src/test` or `src/androidTest`
source set exists in any module, and no test-scoped dependency (JUnit, MockK,
Turbine, Compose UI Test, Hilt Testing, ArchUnit) is declared in the version
catalog.

Correctness is verified by:

- **Compilation** — the Gradle dependency graph enforces the module boundaries
  in §2.5 at compile time.
- **`@Preview` rendering** — every screen ships light/dark previews.
- **Manual acceptance** — the per-phase exit checklist in `docs/phases.md`
  (install, launch, walk the flows, check `adb logcat` for crashes).

### Static analysis

| Library | Purpose |
|---|---|
| Detekt | Kotlin lint |
| Ktlint | Formatter (CI fails on violations) |
| Android Lint | Built-in |

---

## 4. File & naming conventions

### Files

- One top-level class per file (Kotlin idiom).
- File name = class name.
- Nested classes stay in the same file as their parent.
- **Max ~300 lines per file**; refactor when exceeded.

### Naming

| What | Convention | Example |
|---|---|---|
| Class / object / interface | PascalCase | `LoopRepository` |
| Function / property | camelCase | `observeLoops()` |
| Composable | PascalCase, ends with what it returns | `HomeScreen`, `KornerzButton` |
| Screen composable | `<Feature>Screen` | `HomeScreen` |
| Stateless content composable | `<Feature>Content` | `HomeContent` |
| ViewModel | `<Feature>ViewModel` | `HomeViewModel` |
| UiState | `<Feature>State` | `HomeState` |
| UiEvent | `<Feature>Event` | `HomeEvent` |
| Sealed UiEvent subtypes | PascalCase data objects/classes | `HomeEvent.LoopClicked` |
| Repository | `<Domain>Repository` | `LoopRepository` |
| Module-level constants | camelCase in `object` | `Spacing.space8` |
| AsyncValue subtype | `Success` / `Error` / (no `Loading` prefix) | `AsyncValue.Loading` |

### Composable parameter order

Last parameter is always `Modifier` (Kotlin convention, aids overload resolution):

```kotlin
@Composable
fun KornerzButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,         // always last
    enabled: Boolean = true,
    style: KornerzButtonStyle = KornerzButtonStyle.Filled,
)
```

### Folder structure within a feature

```
feature/home/
├── src/main/kotlin/com/kornerz/home/
│   ├── HomeScreen.kt              ← @Composable entry
│   ├── HomeContent.kt             ← stateless render
│   ├── HomeViewModel.kt           ← @HiltViewModel
│   ├── HomeState.kt               ← data class
│   ├── HomeEvent.kt               ← sealed interface
│   ├── NavEvent.kt                ← navigation events (if any)
│   ├── components/                ← screen-private composables
│   │   └── HomeHeader.kt
│   └── di/
│       └── HomeModule.kt          ← feature-scoped Hilt module (rare)
```

### Imports

```kotlin
// 1. Android / Kotlin stdlib
// 2. Third-party (alphabetical by package)
// 3. Internal (alphabetical by package)
// 4. Blank line
// 5. Static imports (if any)
```

One blank line between groups. Ktlint enforces.

---

## 5. Shared composables in `core/ui/components/`

Reusable across every screen. Each feature consumes these; never re-implements.

| Component | Used in |
|---|---|
| `KornerzButton` (filled, outlined, text variants) | Every form, every CTA |
| `KornerzTextField` (bordered, capsule, transparent) | Auth, profile, search |
| `KornerzPasswordField` | Auth flows |
| `KornerzAvatar` (image + fallback initials) | Comments, lists, headers |
| `KornerzEmptyState` (icon + title + subtitle + CTA slot) | Empty tabs (P0), no-data (P10) |
| `KornerzErrorState` (with Retry button) | Error scenarios |
| `KornerzLoadingView` (shimmer) | Loading scenarios |
| `KornerzTopAppBar` | Detail screens, sub-flows |
| `KornerzChip` | Loops filter (P5) |
| `KornerzOtpTextField` | Auth (P1) — mirrors iOS `AEOTPTextField` |
| `KornerzRichTextEditor` (AndroidView host for WebView) | Article creation (P7) |
| `KornerzImagePicker` (PhotoPicker wrapper) | Signup (P1), creation (P7) |

**Each component contract:**
- Takes primitive parameters + `Modifier` last.
- Has a `@Preview` showing the component in both light and dark themes.
- Has an optional overload that accepts `AsyncValue<T>` for ergonomic loading/error handling.
- Has no business logic; delegates everything via callbacks.

---

## 6. Verification approach

The Android port does not maintain automated tests. The checks below replace
them:

| Layer | How it is verified | Tool |
|---|---|---|
| Module deps | Compile-time — `core/*` cannot resolve `feature/*` | Gradle `implementation` / `api` |
| ViewModel | Manual walkthrough of every state (loading / empty / error / content) | App launch + `adb logcat` |
| Repository | Manual check against the real backend | App launch |
| Composable | `@Preview` in light and dark themes | Android Studio preview |
| Auth flow | Manual end-to-end sign-in / sign-up | Device or emulator |

### What is NOT verified automatically

- Pure data classes (`HomeState`) — no behavior.
- Theme tokens — verified by `@Preview` rendering.
- Singleton Hilt providers — verified by app launch.
- Backend mapping — verified by exercising the real Firestore / Cloud
  Functions paths in the running app.

---

## 7. Clean code rules (enforced)

1. **No business logic in Composables.** Composables are pure renderers. Any state mutation goes through `onEvent` → ViewModel.
2. **No `var` in ViewModels.** `MutableStateFlow` is the only mutable state; expose `StateFlow` (immutable view).
3. **No magic numbers.** Spacing, colors, durations live in tokens (`core/ui/theme/`).
5. **No `companion object` constants.** Use top-level `const val` or `object` (more testable).
6. **No singletons by hand.** All singletons via Hilt `@Singleton`.
7. **No `runCatching` in Composables.** Errors are `AsyncValue.Error`.
8. **No `!!` (force unwrap).** Use `requireNotNull` with a meaningful message, or a sealed error type.
9. **Composable parameters end with `Modifier`.** Kotlin convention; aids overload resolution.
10. **ViewModels never reference `NavController` directly.** Navigation goes through `Channel<NavEvent>`.

---

## 8. Anti-patterns — DO NOT

- **No god ViewModels.** One ViewModel per screen. Cross-screen state via shared Repository, not via god VM.
- **No shared mutable state outside Hilt.** No `object Repository { var user = ... }`.
- **No premature abstractions.** Build the screen first; extract `HomeHeader` only when a second screen needs it.
- **No DI by service locator.** Hilt only; no `App.container.get()` patterns.
- **No iOS-style coordinators as Compose state holders.** UDF + Navigation-Compose handles nav.
- **No data class for UI state with no logic.** If a screen only shows a string, no ViewModel needed.
- **No libraries not in §3.** New dependencies require an OpenSpec amendment to this document.

---

## 9. Convention plugins (Gradle module templates)

To stop every `core/*` and `feature/*` module's `build.gradle.kts` from drifting, we use small convention plugins in `KornerzAndroid/build-logic/`:

| Convention plugin | Applies |
|---|---|
| `kornerz.android.library` | `com.android.library`, Kotlin, minSdk |
| `kornerz.android.application` | `com.android.application`, Kotlin, kapt |
| `kornerz.android.compose` | Compose compiler, BOM |
| `kornerz.android.hilt` | Hilt + kapt |
| `kornerz.android.feature.compose.hilt` | Library + Compose + Hilt + coroutines (typical feature module) |

Each module's `build.gradle.kts` then becomes:

```kotlin
plugins {
    id("kornerz.android.feature.compose.hilt")
}

android { namespace = "com.kornerz.feature.home" }
```

Two lines, consistent setup. Adding a new feature module is trivial.

---

## 10. What adding a new screen looks like

A new engineer adds a "Profile" screen in `feature/profile/`:

```
1. Create folder feature/profile/
2. Touch build.gradle.kts → apply `kornerz.android.feature.compose.hilt`
3. Write ProfileState.kt   (data class)
4. Write ProfileEvent.kt   (sealed interface)
5. Write ProfileNavEvent.kt (if navigation needed)
6. Write ProfileRepository.kt (interface) + ProfileRepositoryImpl.kt
7. Write ProfileViewModel.kt (@HiltViewModel, injects ProfileRepository)
8. Write ProfileScreen.kt  (@Composable, calls hiltViewModel())
9. Write ProfileContent.kt (stateless render)
10. Write components/ subfolder for screen-private composables
11. Wire into NavHost in feature/shell
```

Same shape for every screen.

---

## 11. Compliance

- **Every OpenSpec spec in this repository MUST include a `## References` section linking to this document** (`./docs/architecture.md` from the spec's perspective, or relative `../../../../docs/architecture.md`).
- A spec without this link is non-compliant and should be flagged during review.
- Amendments to this document require a new OpenSpec change that explicitly modifies the affected patterns.