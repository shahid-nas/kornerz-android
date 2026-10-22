# android-build delta Specification

## Purpose
TBD - created by archiving change phase-8-premium-iap. Update Purpose after archive.

## MODIFIED Requirements

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

## ADDED Requirements

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
- dependencies: `project(":core:ui")`, `project(":core:model")`, `project(":core:network")`, `project(":core:storage")`, Compose BOM + `ui` / `ui-graphics` / `ui-tooling-preview` / `material3` / `material-icons-extended`, `navigation-compose`, `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`, `hilt-android` + `hilt-compiler` (kapt) + `hilt-navigation-compose`, `coil-compose`, `kotlinx-coroutines-android`, `kotlinx-datetime`, `implementation(libs.android.billing)`, and the test stack (`junit`, `mockk`, `turbine`, `kotlinx-coroutines-test`)

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

## References

- `gradle/libs.versions.toml` — the catalog P8 extends
- `settings.gradle.kts` — `RepositoriesMode.FAIL_ON_PROJECT_REPOS` and the `include` list
- `app/build.gradle.kts:12-53` — flavors, SDK levels, and the debug signing config inherited by `release`
- `feature/profile/build.gradle.kts` — the scaffolding template
- `docs/architecture.md:230` — "Google Play Billing v6+ | Subscriptions (P8)"
- `docs/project-brief.md:164` — the `subscription/` module in the prescribed module tree