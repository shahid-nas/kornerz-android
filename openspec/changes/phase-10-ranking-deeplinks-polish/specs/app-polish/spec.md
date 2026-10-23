# app-polish Specification

## ADDED Requirements

### Requirement: The retry view mirrors the iOS RetryView
`core/ui/components/` SHALL declare a `KornerzRetryView` composable mirroring iOS `Components/RetryView.swift`.

It SHALL render, vertically centred: the error message at 12 pt, centred and wrapping over multiple lines; and below it, a capsule button labelled `Retry again` whose label is the value of iOS's `retry_again` localisation key. The button SHALL use a grey tint, matching iOS's `.buttonStyle(.borderedProminent)` with `.tint(.gray)` and its 20-pt corner radius.

The component SHALL take the error message, a retry callback, and an optional modifier. It SHALL NOT be tied to a single error type.

#### Scenario: The retry view renders the message and the action
- **WHEN** `KornerzRetryView("Failed to load", onRetry = {})` is composed
- **THEN** the message "Failed to load" and a button labelled "Retry again" are rendered

#### Scenario: The retry action fires
- **WHEN** the "Retry again" button is tapped
- **THEN** the supplied `onRetry` callback is invoked exactly once

#### Scenario: The label matches the iOS string
- **WHEN** the component's button label is read
- **THEN** it is `Retry again`, the English value of iOS's `retry_again` key

### Requirement: The no-data view mirrors the iOS NoDataView
`core/ui/components/` SHALL declare a `KornerzNoDataView` composable mirroring iOS `Components/NoDataView.swift`.

It SHALL render a 75 × 75 dp illustration above a title set in the Kornerz typography scale's body size, with 50 dp of vertical padding. The illustration SHALL be a parameter so a designer-supplied asset can be substituted without changing the component; the default SHALL be a Material icon.

The component SHALL take the title, an optional icon, and an optional modifier.

#### Scenario: The no-data view renders a title and an illustration
- **WHEN** `KornerzNoDataView("No communities yet")` is composed
- **THEN** the title "No communities yet" and a 75 dp illustration are rendered

#### Scenario: The illustration can be replaced
- **WHEN** `KornerzNoDataView("No communities yet", icon = Icons.Filled.Groups)` is composed
- **THEN** the supplied icon is rendered instead of the default

### Requirement: The loading-more view mirrors the iOS LoadingMoreView
`core/ui/components/` SHALL declare a `KornerzLoadingMoreView` composable mirroring iOS `Components/LoadingMoreView.swift`.

It SHALL render a horizontal row, centred and full-width with padding, containing a progress indicator and, beside it with 16 dp of spacing, a label whose text is the value of iOS's `loading_more_items` localisation key.

The component SHALL take an optional modifier.

#### Scenario: The loading-more view renders
- **WHEN** `KornerzLoadingMoreView()` is composed
- **THEN** a progress indicator and the label "Loading more items" are rendered side by side, centred

#### Scenario: The label matches the iOS string
- **WHEN** the component's label text is read
- **THEN** it is `Loading more items`, the English value of iOS's `loading_more_items` key

### Requirement: List screens standardise on the shared loading-more view
Every paginated list screen SHALL render the shared `KornerzLoadingMoreView` as its pagination footer, replacing the ad-hoc spinner each screen inlines.

The existing `KornerzEmptyState`, `KornerzErrorState`, and `KornerzLoadingView` components SHALL NOT be removed. They remain the generic components earlier phases use.

#### Scenario: The notifications footer uses the shared view
- **WHEN** the notifications list is loading a subsequent page
- **THEN** the footer renders `KornerzLoadingMoreView` rather than a bare progress indicator

#### Scenario: The existing generic components remain
- **WHEN** `core/ui/components/` is enumerated
- **THEN** `KornerzEmptyState`, `KornerzErrorState`, and `KornerzLoadingView` are all still present

### Requirement: Every icon-only control carries a content description
Every icon-only control on the screens touched by P10 SHALL carry a non-empty content description that names the action, in the same language as the surrounding UI (English, per `project-brief.md` §11.7).

A decorative icon that conveys no action SHALL carry `null` as its content description so it is skipped by accessibility services.

#### Scenario: A back control is described
- **WHEN** a screen with an icon-only back button is inspected
- **THEN** the button's content description is a non-empty string naming the action

#### Scenario: Decorative icons are skipped
- **WHEN** a purely decorative icon is rendered
- **THEN** its content description is `null`

### Requirement: Text respects the system font scale
No text container on the screens touched by P10 SHALL have a fixed height that clips its content when the system font scale is increased. Text SHALL be laid out with content-driven height.

#### Scenario: Text grows with the font scale
- **WHEN** the system font scale is set to the largest setting
- **THEN** the affected text remains fully visible and is not clipped

### Requirement: The release build is minified and shrunk
The `release` build type in `app/build.gradle.kts` SHALL set `isMinifyEnabled = true` and `isShrinkResources = true`.

The `debug` build type SHALL continue to set both to `false`, so the phase's install-and-launch artefact remains fast to build and readable in stack traces.

The release build type SHALL keep its existing signing configuration; P10 does not change how release builds are signed.

#### Scenario: The release variant is minified
- **WHEN** `./gradlew :app:assembleProdRelease` is run
- **THEN** the produced APK is minified and resource-shrunk

#### Scenario: The debug variant is not minified
- **WHEN** `./gradlew :app:assembleDevDebug` is run
- **THEN** `minifyEnabled` is `false` for the debug variant

#### Scenario: The minified APK launches
- **WHEN** the minified release APK is installed on an emulator and launched
- **THEN** the app starts without a fatal exception

### Requirement: The ProGuard rules cover Vonage, Firebase, and kotlinx-serialization
`app/proguard-rules.pro` SHALL carry keep rules for the three categories the shipped dependency set resolves reflectively:

| Category | Why |
|---|---|
| Vonage video SDK | The SDK instantiates its session and media classes from native code and by name. |
| Firebase SDKs | The Firebase SDKs look up classes by name at runtime. |
| kotlinx-serialization | The generated `Companion.serializer()` calls are resolved reflectively. |

The rules SHALL be exercised: the release APK SHALL be assembled, installed, and launched as part of the phase's exit checklist, so a rule that strips a class the app needs is caught by the launch rather than discovered in production.

#### Scenario: The rules are present
- **WHEN** `app/proguard-rules.pro` is inspected
- **THEN** it contains keep rules for the Vonage SDK, the Firebase SDKs, and kotlinx-serialization

#### Scenario: The rules are exercised
- **WHEN** the phase's exit checklist runs
- **THEN** the minified release APK is installed and launched on an emulator without a fatal exception

### Requirement: The module boundary rule is enforced by a test
The module-boundary rule from `docs/architecture.md` §2.5 SHALL be enforced by a test in `app/src/test/`:

- `core.*` SHALL NOT depend on `feature.*` or on `app`.
- `feature.*` SHALL NOT depend on `app`.
- No feature module SHALL depend on another feature module **except** `feature.shell`, which owns the tab-graph seam (`feature.shell` → `feature.home`, `feature.kornerz`, `feature.loops`, `feature.nooks`, `feature.videocall`, `feature.profile`, `feature.notifications`, `feature.community`, `feature.ranking`, and `feature.subscription`'s route strings only).

The test SHALL be implemented with ArchUnit (`com.tngtech.archunit:archunit-junit4`), the tool `docs/architecture.md:262` and `:387` mandate for P10.

If the ArchUnit artifact cannot be resolved in the build environment, the same rules SHALL be enforced by an equivalent JUnit test that scans the modules' Kotlin sources for forbidden import statements. The fallback enforces the same rules with no new dependency and is an accepted outcome, not a failure.

#### Scenario: A core-to-feature dependency fails the test
- **WHEN** a `core.model` class imports a `com.kornerz.feature.*` class
- **THEN** the module-boundary test fails

#### Scenario: A feature-to-feature dependency fails the test
- **WHEN** a `feature.loops` class imports a `com.kornerz.feature.nooks` class
- **THEN** the module-boundary test fails

#### Scenario: The shell's tab-graph seam is allowed
- **WHEN** a `feature.shell` class imports a `com.kornerz.feature.home` class
- **THEN** the module-boundary test passes

#### Scenario: The current tree passes
- **WHEN** the module-boundary test runs against the P10 tree
- **THEN** it passes
