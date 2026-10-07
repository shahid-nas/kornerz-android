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

In P0, `feature/*` modules contain only placeholder composables. `core/network/` initializes Firebase. `core/model/`, `core/storage/` are empty libraries.

#### Scenario: Gradle settings includes all modules
- **WHEN** `KornerzAndroid/settings.gradle.kts` is opened
- **THEN** every module above is declared via `include(":module-name")`

#### Scenario: Each module builds independently
- **WHEN** `./gradlew :feature:shell:assembleDebug` is run from the project root
- **THEN** the command succeeds without referencing `feature/splash` or other unneeded modules

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
The `app/` module SHALL include `proguard-rules.pro` with baseline keep rules for: Compose runtime, Hilt-generated classes, Firebase, Kotlin reflection. No shrinking is enabled in P0.

#### Scenario: Debug build does not shrink
- **WHEN** `./gradlew :app:assembleDevDebug` is run
- **THEN** `minifyEnabled` is `false` for the debug variant
- **AND** the resulting APK is not shrunk or obfuscated

#### Scenario: Release build has rules applied
- **WHEN** `./gradlew :app:assembleProdRelease` is run (future phase)
- **THEN** R8 picks up `proguard-rules.pro` and applies the baseline keep rules

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

#### Scenario: Version catalog has expected entries
- **WHEN** `KornerzAndroid/gradle/libs.versions.toml` is opened
- **THEN** it contains at minimum the dependency aliases listed above

