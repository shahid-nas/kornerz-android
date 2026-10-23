# android-build Specification

## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: The version catalog carries the test-only module-boundary dependency
`gradle/libs.versions.toml` SHALL declare the ArchUnit test dependency (`com.tngtech.archunit:archunit-junit4`) as a version-catalog entry, referenced via a `libs.*` accessor. The dependency SHALL be `testImplementation`-scoped in `app/build.gradle.kts` and SHALL NOT be applied to any production source set.

The project's JUnit 4 runner is the reason the `archunit-junit4` artifact is used rather than `archunit-junit5`.

#### Scenario: The dependency is catalogued
- **WHEN** `gradle/libs.versions.toml` is inspected
- **THEN** it declares an ArchUnit entry referenced as `libs.*`

#### Scenario: The dependency is test-scoped
- **WHEN** `app/build.gradle.kts` is inspected
- **THEN** the ArchUnit dependency is declared with `testImplementation` and is not declared with `implementation` or `api`

### Requirement: The release build type keeps its signing configuration
P10 SHALL NOT change how the release build type is signed. The release build type SHALL continue to use the existing signing configuration, and no upload key, keystore, or credential SHALL be introduced by this change.

The minified release APK produced by P10 is install-and-launch verified only; it is not uploadable to Play.

#### Scenario: The signing configuration is unchanged
- **WHEN** `app/build.gradle.kts` is compared against its pre-P10 state
- **THEN** the release build type's `signingConfig` is unchanged

#### Scenario: No signing secret is introduced
- **WHEN** the P10 diff is inspected
- **THEN** no keystore, key alias, or signing password appears in the repository
