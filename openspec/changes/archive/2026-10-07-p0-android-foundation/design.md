## Context

The Kornerz iOS app at `kornerz/` is the source of truth for product, design, and behavior. This design creates the Android project skeleton that all subsequent phases (P1–P10 in `docs/phases.md`) will build on.

**Current state:** `KornerzAndroid/` exists as an empty directory. No Android code, no Gradle setup, no Firebase wiring. The iOS app provides the brand and feature reference via `kornerz/Kornerz/Helper/Config.swift` (colors, fonts), `kornerz/Kornerz/SwiftUI/TabView/AppTabController.swift` (tab structure), and `kornerz/Kornerz/App/AppDelegate.swift` (Firebase init pattern).

**Stakeholders:** Android team (primary), Design (visual parity with iOS), Backend (Firebase project access), QA (CI build).

**Constraints (locked decisions from `docs/project-brief.md` §11):**
- Compose-first; `AndroidView` wrappers only where vendored SDKs force it (none in P0).
- Shared Firebase project `kornerz-72fba`.
- `minSdk = 26`, `targetSdk = latest stable`.
- English only at launch.
- Module structure per `project-brief.md` §4.2.
- Live screens only — no legacy UIKit port (irrelevant for Android, but no dead code in either app).

## Goals / Non-Goals

**Goals:**
- Working Android project that builds, installs, and launches into a branded splash → 5-tab empty shell.
- Visual brand parity with iOS (colors, Satoshi font, dark/light mode).
- CI gates every PR with a working APK.
- Module structure ready for P1+ to drop in feature modules without restructuring.
- Firebase initialized and connected to the shared project; anonymous user setup in place for P1 to upgrade.

**Non-Goals:**
- No business logic (no User model, no repositories, no auth flows) — that's P1+.
- No content rendering in tabs — each tab shows an "Empty state" placeholder until P2+.
- No push notification handling UI — FCM token plumbing only (consumed by P7).
- No video player — ExoPlayer dependency declared but unused in P0.
- No app icon design — placeholder adaptive icon, designer finalizes in a follow-up.
- No deep-link handling — P10.
- No localization infrastructure — single `values/strings.xml` only (English only).

## Decisions

### 1. Compose-first with Material 3

**Choice:** Jetpack Compose with `androidx.compose:compose-bom` + Material 3.

**Rationale:** Matches iOS's directional migration toward SwiftUI (§11.1 of `project-brief.md`). Greenfield — no legacy to migrate. Google's strategic commitment to Compose; new Material 3 lives there.

**Alternatives considered:**
- *Views + Compose hybrid* — rejected; commits us to a permanent split-state, the same debt iOS is paying.
- *Views only* — rejected; ignores iOS's destination and gives no advantage in P0.

### 2. Module structure mirrors `project-brief.md` §4.2

```
KornerzAndroid/
├── app/                          ← ApplicationModule (entry point)
├── core/
│   ├── model/                    ← Placeholder for domain types (P1+)
│   ├── network/                  ← Firebase init only in P0
│   ├── storage/                  ← Placeholder
│   └── ui/                       ← Design tokens, theme, shared composables
├── feature/
│   ├── splash/                   ← Branded splash screen
│   └── shell/                    ← 5-tab navigation host
├── gradle/
│   └── libs.versions.toml        ← Version catalog
└── build.gradle.kts (root)
```

**Rationale:** Locked in `project-brief.md`. P0 seeds every module; later phases fill them.

**Alternatives considered:**
- *Single-module app* — rejected; bloats build times and prevents feature-team parallelization.
- *More granular modules per feature* — rejected; YAGNI for P0. P2+ can split `feature/shell` into per-tab modules once content lands.

### 3. Navigation: `androidx.navigation:navigation-compose`

**Choice:** Navigation-Compose with a single `NavHost` at `app/` level. Each tab is a nested graph.

**Rationale:** First-party, well-documented, integrates with Hilt via `hiltViewModel()`. Mirrors SwiftUI's `NavigationStack` semantics closely enough for engineers moving between apps.

**Alternatives considered:**
- *Voyager* — popular third-party Compose navigator; adds a dependency for no clear win in P0.
- *Decompose* — overkill for an app of this complexity.
- *Custom coordinator pattern* — mirrors iOS `BaseCoordinator` more literally, but adds friction with Compose recomposition. P10 can revisit if deep-link flow demands it.

### 4. DI: Hilt

**Choice:** `com.google.dagger:hilt-android` + `androidx.hilt:hilt-navigation-compose`.

**Rationale:** Google's recommended Android DI. `hiltViewModel()` integrates cleanly with Navigation-Compose. Mirrors iOS's `Dependencies` framework ergonomically.

**Alternatives considered:**
- *Koin* — simpler but runtime-resolved; Hilt's compile-time validation is worth the friction.
- *Manual DI* — rejected; scales poorly.

### 5. Theme structure: `Material 3 ColorScheme` + Kornerz tokens

**Choice:** Material 3 `lightColorScheme()` / `darkColorScheme()` populated from `core/ui/theme/Color.kt`. Component composables (`KornerzButton`, `KornerzTextField`, etc.) live in `core/ui/components/` and consume tokens via `MaterialTheme.colorScheme`.

**Rationale:** Idiomatic Material 3. Tokens flow through `MaterialTheme` so any Composable in P1+ gets brand colors automatically via `MaterialTheme.colorScheme.primary`.

**Token mapping (iOS `Config.swift` → Android hex):**
- iOS uses hex-prefixed names: `C5D47ED` = `#5D47ED` (strip leading `C`), `C353446` = `#353446`, etc.
- P0 ports the full palette (~34 colors) into `core/ui/theme/Color.kt` as named constants.
- Typography: Satoshi family bundled in `core/ui/src/main/res/font/` (10 `.otf` files copied from `kornerz/Kornerz/Fonts/`).

**Alternatives considered:**
- *Custom theme system* — rejected; no benefit over Material 3 for an app of this scale.
- *Material 2* — rejected; legacy.

### 6. Build flavors: `dev` + `prod`

**Choice:** Two product flavors:
- `dev` → `applicationIdSuffix = ".dev"`, app name `"Kornerz Dev"`, points to `kornerz-72fba` Firebase project. Mirrors iOS `KornerzDev` target.
- `prod` → standard, app name `"Kornerz"`, same Firebase project but distinct `google-services.json`.

**Rationale:** Matches iOS two-target structure (`KornerzDev` + `Kornerz`). Lets QA and engineering run dev + prod side by side.

**Alternatives considered:**
- *Single flavor* — rejected; breaks QA workflow.
- *More flavors (staging, etc.)* — rejected; YAGNI.

### 7. CI: GitHub Actions

**Choice:** `.github/workflows/android-pr.yml` runs on PR open/update:
1. Checkout
2. Setup JDK 17
3. Cache Gradle
4. `./gradlew :app:assembleDevDebug`
5. Upload APK as build artifact

**Rationale:** Lowest friction, lives next to the code, free for public/internal repos.

**Alternatives considered:**
- *Bitrise* — used by some iOS pipelines but adds external tool.
- *Local-only* — rejected; defeats the purpose.

### 9. Anonymous Firebase user setup

**Choice:** In P0, after `FirebaseApp.configure()`, call `FirebaseAuth.getInstance().signInAnonymously()` on app start. This gives a stable identifier before P1's custom-auth flow upgrades it.

**Rationale:** Pre-populates Firestore-side user tracking so P1's login/spend can link anonymous activity to the real account. Mirrors iOS's "anonymous until Cloud Function auth" pattern.

**Alternatives considered:**
- *No auth until P1* — rejected; Firestore rules would need a public path or every doc would be inaccessible. Anonymous auth is the simplest secure default.
- *Firebase Auth email/password from P0* — rejected; P1 uses custom Cloud Functions, not FirebaseAuth.

## Risks / Trade-offs

- **Firebase Auth anonymous setup adds a dependency P1 will replace.** P0 ships `firebase-auth` only for the anonymous sign-in; P1+ may keep it as a marker but replace the auth token path with custom-auth Cloud Function tokens. *Mitigation:* P1 task explicitly audits and removes unnecessary FirebaseAuth surfaces.
- **`google-services.json` must be checked into the repo.** Contains public OAuth client IDs, not secrets, but should not be in feature branches. *Mitigation:* committed at default branch only; CI uses the same file.
- **Satoshi font licensing.** Bundling 10 `.otf` files in `res/font/` is fine for app distribution; do not extract to public assets. *Mitigation:* documented in `core/ui/src/main/res/font/README.md`.
- **Tab icons are placeholders.** Final brand icons from Design team land post-P0. *Mitigation:* P0 uses Material Icons; design task tracked separately.
- **`forumTabController()` iOS anomaly has no Android equivalent.** iOS defines a Forum tab controller but never adds it to `viewControllers`. Android does NOT replicate the dead tab. P5 covers the active Loops surface only. *Mitigation:* documented in `phases.md`.
- **Compose BOM pinning.** Compose BOM can shift breaking changes between minor versions. *Mitigation:* version catalog pins to a tested BOM; CI catches mismatches.
- **App size budget not yet measurable.** 60 MB ceiling per `project-brief.md` §10. *Mitigation:* P10 measures and adjusts.

## Migration Plan

Not applicable — greenfield. No existing Android users to migrate. The Android app ships as a new install; users start at version 1.0.0.

## Open Questions

1. **CI runner flavor.** GitHub-hosted Linux runners are free for public repos; macOS runners are slower + cost. Build matrix size? — *Decision needed before merging.*
2. **Adaptive icon design.** Material 3 adaptive icons need a foreground + background. Designer deliverable or P0 ships with Material default? — *Default: Material default; designer polish in follow-up.*
3. **Splash icon.** Use product icon, custom splash illustration, or Material default? — *Default: branded initial logo; designer polish in follow-up.*
4. **Theme dark mode parity.** iOS `Config.swift` doesn't define dark variants explicitly — colors are static hex. Android will need to choose light/dark mappings per token. *Decision needed in P0 design pass; will pull from product for reference.*
6. **Crash reporting.** Firebase Crashlytics from P0 or deferred? — *Recommendation: yes, P0; one-line integration; gives baseline observability from day one.*

## References

- [Architecture, Patterns & Conventions](../../../../docs/architecture.md) — canonical reference. Every decision in this document conforms to it. Implementation MUST follow it.
- [Project Brief](../../../../docs/project-brief.md) — §11 locked decisions (Compose-first, Vonage, shared Firebase `kornerz-72fba`, full parity, separate codebase, minSdk 26, English only).
- [Phased Delivery Plan](../../../../docs/phases.md) — phase boundaries; this is P0.