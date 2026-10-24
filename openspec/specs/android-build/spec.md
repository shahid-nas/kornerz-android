# android-build Specification

## Purpose
TBD - created by archiving change p0-android-foundation. Update Purpose after archive.
## Requirements
### Requirement: Multi-module Gradle project structure
The Android project SHALL be structured as a multi-module Gradle project. The top-level `KornerzAndroid/` directory SHALL contain the following modules, each as a separate Gradle subproject:

- `app/` (application module, depends on all `core.*` and `feature.*`)
- `core/model/` (Kotlin library)
- `core/network/` (Kotlin library)
- `core/storage/` (Kotlin library)
- `core/ui/` (Android library, depends on Compose)
- `feature/splash/` (Android library)
- `feature/shell/` (Android library)
- `feature/ranking/` (Android library, introduced in P10 — the Klouders leaderboard)

In P0, `feature/*` modules contain only placeholder composables. `core/network/` initializes Firebase. `core/model/`, `core/storage/` are empty libraries.

`feature/ranking` SHALL be declared via `include(":feature:ranking")` in `settings.gradle.kts` and SHALL be imported by `app` and by `feature/shell` (which owns the tab graph), and by no other module.

#### Scenario: Gradle settings includes all modules
- **WHEN** `KornerzAndroid/settings.gradle.kts` is opened
- **THEN** every module above is declared via `include(":module-name")`

#### Scenario: Each module builds independently
- **WHEN** `./gradlew :feature:shell:assembleDebug` is run from the project root
- **THEN** the command succeeds without referencing `feature/splash` or other unneeded modules

#### Scenario: The ranking module builds independently
- **WHEN** `./gradlew :feature:ranking:assembleDevDebug` is run from the project root
- **THEN** the command succeeds

### Requirement: Version catalog for dependency management
The project SHALL use Gradle's version catalog at `KornerzAndroid/gradle/libs.versions.toml`. All third-party dependency versions MUST be declared in the catalog and referenced via `libs.X.Y` accessors in module `build.gradle.kts` files. Hardcoded version strings in module build files are not permitted.

#### Scenario: Library versions centralized
- **WHEN** any module's `build.gradle.kts` declares a dependency
- **THEN** the version is referenced via `libs.versions.X` or `libs.X`, not hardcoded

### Requirement: Compose BOM pinned in version catalog
The version catalog SHALL declare the Jetpack Compose BOM version, and all Compose module dependencies SHALL pull versions from the BOM (no explicit Compose module versions).

#### Scenario: Compose modules use BOM
- **WHEN** `core/ui/build.gradle.kts` declares Compose dependencies
- **THEN** each Compose module line omits the version (uses BOM), e.g. `implementation(platform(libs.compose.bom))` + `implementation(libs.compose.ui)`

### Requirement: Two product flavors: dev and prod
The `app/` module SHALL declare two product flavors in its `build.gradle.kts`:
- `dev` with `applicationIdSuffix = ".dev"`, app name `"Kornerz Dev"`
- `prod` with no `applicationIdSuffix`, app name `"Kornerz"`

#### Scenario: Dev variant installs alongside prod
- **WHEN** both `assembleDevDebug` and `assembleProdDebug` APKs are installed on the same device
- **THEN** both apps are visible in the launcher as separate apps (different icons/labels)

#### Scenario: Dev variant name
- **WHEN** user opens the dev variant on a device
- **THEN** the app label shown in the launcher is "Kornerz Dev"

#### Scenario: Prod variant name
- **WHEN** user opens the prod variant on a device
- **THEN** the app label shown in the launcher is "Kornerz"

### Requirement: Firebase project wired
The `app/` module SHALL include `google-services.json` and apply the `com.google.gms.google-services` Gradle plugin. The Firebase project ID SHALL be `kornerz-72fba` (the same project used by the iOS app).

#### Scenario: Firebase initializes at runtime
- **WHEN** the app process starts
- **THEN** `FirebaseApp.getInstance()` returns a non-null instance
- **AND** the project ID equals `kornerz-72fba`

#### Scenario: Crashlytics enabled
- **WHEN** an unhandled exception occurs in any composable
- **THEN** the crash is reported to Firebase Crashlytics within 30 seconds

### Requirement: Min SDK 26, target SDK latest stable
The `app/` module's `build.gradle.kts` SHALL set `minSdk = 26` and `targetSdk = <latest stable>` (e.g., 35 at time of writing).

#### Scenario: Installs on API 26 device
- **WHEN** an APK is installed on a device running Android 8.0 (API 26)
- **THEN** installation succeeds
- **AND** the app launches without `INSTALL_FAILED_OLDER_SDK`

#### Scenario: Optimized for latest API
- **WHEN** the app runs on a device at `targetSdk` or higher
- **THEN** it uses API-appropriate behaviors (edge-to-edge, predictive back, etc.)

### Requirement: CI workflow builds dev APK on every PR
The repository SHALL include a GitHub Actions workflow at `.github/workflows/android-pr.yml` that runs on `pull_request` events touching `KornerzAndroid/**`. The workflow SHALL:
1. Check out the repository
2. Set up JDK 17
3. Cache Gradle (`~/.gradle/caches`)
4. Run `./gradlew :app:assembleDevDebug`
5. Upload the resulting APK as a build artifact

#### Scenario: PR with valid code
- **WHEN** a pull request is opened or updated that modifies `KornerzAndroid/**`
- **THEN** the workflow runs within 5 minutes
- **AND** the build step exits with code 0
- **AND** a downloadable APK artifact is attached to the workflow run

#### Scenario: PR with build failure
- **WHEN** a pull request introduces code that fails compilation
- **THEN** the workflow run is marked failed
- **AND** the PR cannot be merged until the build is fixed (if branch protection is enabled)

### Requirement: ProGuard / R8 baseline rules in place
The `app/` module SHALL include `proguard-rules.pro` with keep rules for: Compose runtime, Hilt-generated classes, Firebase, Kotlin reflection, the Vonage video SDK, kotlinx-serialization's generated serializers, and the `@Serializable` model classes.

The release build type SHALL enable R8 minification (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`) so the rules are exercised. The debug build type SHALL keep both disabled.

#### Scenario: Debug build does not shrink
- **WHEN** `./gradlew :app:assembleDevDebug` is run
- **THEN** `minifyEnabled` is `false` for the debug variant
- **AND** the resulting APK is not shrunk or obfuscated

#### Scenario: Release build has rules applied
- **WHEN** `./gradlew :app:assembleProdRelease` is run
- **THEN** R8 picks up `proguard-rules.pro` and applies the keep rules
- **AND** `minifyEnabled` is `true` and `shrinkResources` is `true` for the release variant

#### Scenario: The Vonage and Firebase rules are present
- **WHEN** `app/proguard-rules.pro` is inspected
- **THEN** it contains keep rules for the Vonage SDK, the Firebase SDKs, and kotlinx-serialization

#### Scenario: The minified APK still launches
- **WHEN** the minified release APK is installed and launched on an emulator
- **THEN** the app starts without a fatal exception

### Requirement: Dependencies declared in P0
The version catalog SHALL declare these P0 dependencies (minimum):
- `kotlin` (Android Kotlin plugin version)
- `android-gradle-plugin`
- `compose-bom`
- `compose-ui`, `compose-ui-tooling-preview`, `compose-material3`
- `compose-ui-tooling` (debug only)
- `activity-compose`
- `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`
- `navigation-compose`
- `hilt-android`, `hilt-compiler` (kapt or ksp), `hilt-navigation-compose`
- `coil-compose`
- `firebase-bom`, `firebase-auth-ktx`, `firebase-firestore-ktx`, `firebase-crashlytics-ktx`
- `google-services` Gradle plugin
- `media3-exoplayer` (declared for P9, unused in P0)
- `lifecycle-process` (for `DefaultLifecycleObserver`)

**P8 addition:** the catalog SHALL additionally declare exactly one
P8 dependency:

| Catalog alias | Group : artifact | Version | Phase |
|---|---|---|---|
| `android-billing` | `com.android.billingclient` : `billing-ktx` | `7.0.0` | P8 |

Version 7.0.0 satisfies the `docs/phases.md:259` floor of "Google Play
Billing v6+"; v7 is chosen because v6 is end-of-life. The library
requires `minSdk >= 21`, which the project's `minSdk = 26` already
satisfies, and imposes no `compileSdk` or AGP floor above the project's
existing `compileSdk = 35` / AGP 8.5.2.

This is the **first Play-services dependency in the codebase** — the
artifact is entirely absent today (0 hits for `billingclient` /
`BillingClient` / `play.core` outside generated build artifacts).

`android-billing` SHALL be consumed as `implementation(libs.android.billing)`
by `feature/subscription` (the module that owns `BillingClientApi`)
and by `app` (which needs it on the compile classpath for the
Play-services license-check surface and for manifest merging). It SHALL
NOT be added to `core/network`, `core/model`, `core/ui`, or any other
module, so the Play dependency's blast radius stays at two modules.

Because `settings.gradle.kts` declares
`RepositoriesMode.FAIL_ON_PROJECT_REPOS`, the Google Maven repository
is already covered by the root `google()` entry; P8 SHALL NOT add a
per-module `repositories {}` block.

The Play Billing AAR contributes `com.android.vending.BILLING` to the
merged manifest automatically, so `app/src/main/AndroidManifest.xml`
SHALL NOT gain an explicit `<uses-permission>` for it.

#### Scenario: Version catalog has expected entries
- **WHEN** `KornerzAndroid/gradle/libs.versions.toml` is opened
- **THEN** it contains at minimum the dependency aliases listed above

#### Scenario: The billing alias resolves
- **WHEN** `libs.android.billing` is referenced
- **THEN** it resolves to `com.android.billingclient:billing-ktx:7.0.0` from Google Maven

#### Scenario: No per-module repository block is added
- **WHEN** P8's diff is reviewed for `repositories {` blocks
- **THEN** none are added, preserving `RepositoriesMode.FAIL_ON_PROJECT_REPOS`

#### Scenario: Play dependency is confined to two modules
- **WHEN** `./gradlew :feature:subscription:dependencies` and the other modules' dependency trees are compared
- **THEN** `com.android.billingclient` appears only under `feature:subscription` and `app`

#### Scenario: No explicit billing permission in the manifest
- **WHEN** the merged manifest is inspected
- **THEN** `com.android.vending.BILLING` is present via the library's manifest, and `app/src/main/AndroidManifest.xml` contains no hand-written billing `<uses-permission>`

#### Scenario: The dev flavor still builds
- **WHEN** `./gradlew :app:assembleDevDebug` runs
- **THEN** it succeeds with the billing dependency present, since dependency resolution is independent of whether the application id is a registered Play listing

### Requirement: feature/subscription module is registered
`settings.gradle.kts` SHALL include `:feature:subscription`, and
`app/build.gradle.kts` SHALL declare `implementation(project(":feature:subscription"))`.

The module SHALL use the standard `feature/*` Gradle scaffolding,
mirroring `feature/profile/build.gradle.kts`:

- `namespace = "com.kornerz.feature.subscription"`
- `compileSdk = 35`, `minSdk = 26`
- Java/JVM target 17, `jvmTarget = "17"`
- `buildFeatures { compose = true }`
- plugins: `android-library`, `kotlin-android`, `kotlin-kapt`, `kotlin-compose`, `hilt`
- `kapt { correctErrorTypes = true }`
- dependencies: `project(":core:ui")`, `project(":core:model")`, `project(":core:network")`, `project(":core:storage")`, Compose BOM + `ui` / `ui-graphics` / `ui-tooling-preview` / `material3` / `material-icons-extended`, `navigation-compose`, `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`, `hilt-android` + `hilt-compiler` (kapt) + `hilt-navigation-compose`, `coil-compose`, `kotlinx-coroutines-android`, `kotlinx-datetime`, `implementation(libs.android.billing)`

It SHALL NOT add `kotlin-serialization`: the only new serialized type is `SubscriptionVerificationResult`, which the client-side DTO layer parses with the same `kotlinx.serialization` stack already reachable through `core:network`'s transitive `kotlinx-serialization-json`. (If the build proves this unreachable, adding the plugin is a one-line change confined to this module.)

The module SHALL be imported only by `app`, per `docs/architecture.md` §2.5. No other `feature/*` module SHALL depend on it, so the subscription surface cannot leak into the shell's tab graph — the shell reaches it only through `SubscriptionRoute`.

#### Scenario: Module is registered and wired
- **WHEN** `settings.gradle.kts` and `app/build.gradle.kts` are inspected
- **THEN** `:feature:subscription` is included and `app` declares `implementation(project(":feature:subscription"))`

#### Scenario: Module follows the feature scaffolding
- **WHEN** `feature/subscription/build.gradle.kts` is compared to `feature/profile/build.gradle.kts`
- **THEN** the plugin set, namespace convention, SDK levels, Java target, and Compose/Hilt configuration match, with the billing dependency as the only addition

#### Scenario: Only app depends on the module
- **WHEN** every module's `dependencies` block is inspected
- **THEN** `project(":feature:subscription")` appears only in `app/build.gradle.kts`

#### Scenario: Module builds
- **WHEN** `./gradlew :feature:subscription:assembleDevDebug` runs
- **THEN** it succeeds

### Requirement: The version catalog carries no test-only dependency
`gradle/libs.versions.toml` SHALL NOT declare any test-scoped dependency (`junit`, `androidx-junit`, `espresso`, `mockk`, `turbine`, `kotlinx-coroutines-test`, `archunit-junit4`, `androidx-compose-ui-test-junit4`, `androidx-compose-ui-test-manifest`), and no module's `build.gradle.kts` SHALL declare a `testImplementation` or `androidTestImplementation` configuration. The Android port does not carry an automated test suite, so the catalog carries only production dependencies.

#### Scenario: No test-only catalog entries
- **WHEN** `gradle/libs.versions.toml` is inspected
- **THEN** it declares no JUnit, MockK, Turbine, Espresso, ArchUnit, or Compose UI Test entry

#### Scenario: No test-scoped configurations
- **WHEN** every module's `build.gradle.kts` is inspected
- **THEN** none of them declares `testImplementation` or `androidTestImplementation`, and no module has a `src/test` or `src/androidTest` source set

### Requirement: The release build type keeps its signing configuration
P10 SHALL NOT change how the release build type is signed. The release build type SHALL continue to use the existing signing configuration, and no upload key, keystore, or credential SHALL be introduced by this change.

The minified release APK produced by P10 is install-and-launch verified only; it is not uploadable to Play.

#### Scenario: The signing configuration is unchanged
- **WHEN** `app/build.gradle.kts` is compared against its pre-P10 state
- **THEN** the release build type's `signingConfig` is unchanged

#### Scenario: No signing secret is introduced
- **WHEN** the P10 diff is inspected
- **THEN** no keystore, key alias, or signing password appears in the repository

