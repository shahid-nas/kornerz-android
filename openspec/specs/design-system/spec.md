# design-system Specification

## Purpose
TBD - created by archiving change p0-android-foundation. Update Purpose after archive.
## Requirements
### Requirement: Kornerz color palette defined from iOS tokens
The `core/ui` module SHALL define every color token listed in `kornerz/Kornerz/Helper/Config.swift` `AppColor` struct as a named Kotlin `Color` constant. Each token SHALL have both a light and dark variant. Hex values SHALL be derived by stripping the leading `C` from the iOS name (e.g., `AppColor.C5D47ED` → `Color(0xFF5D47ED)`).

#### Scenario: All iOS palette colors ported
- **WHEN** `core/ui/theme/Color.kt` is enumerated
- **THEN** it contains at least 34 named color constants
- **AND** each constant's hex value matches the corresponding iOS `AppColor.CXXXXXX` value

#### Scenario: Color names match iOS naming
- **WHEN** a Compose component references a Kornerz brand color (e.g., `KornerzPurple`)
- **THEN** the name maps to the iOS `AppColor.CXXXXXX` token it was derived from

### Requirement: Satoshi font family bundled
The `core/ui` module SHALL bundle all 10 Satoshi font weights as Android resources in `core/ui/src/main/res/font/`:
- `satoshi_regular.otf`
- `satoshi_italic.otf`
- `satoshi_light.otf`
- `satoshi_lightitalic.otf`
- `satoshi_medium.otf`
- `satoshi_mediumitalic.otf`
- `satoshi_bold.otf`
- `satoshi_bolditalic.otf`
- `satoshi_black.otf`
- `satoshi_blackitalic.otf`

Source files are at `kornerz/Kornerz/Fonts/` (10 `.otf` files matching these names).

#### Scenario: Font files present
- **WHEN** the module compiles
- **THEN** the 10 `.otf` files are present in `core/ui/src/main/res/font/`
- **AND** Android's font system can resolve each name

#### Scenario: Font loads at app start
- **WHEN** any Compose text uses `Satoshi` font family
- **THEN** the text renders with the Satoshi typeface, not the system fallback

### Requirement: Typography scale defined
The `core/ui` module SHALL define a Material 3 `Typography` with named text styles (`displayLarge`, `headlineMedium`, `titleLarge`, `bodyLarge`, `bodyMedium`, `labelLarge`, etc.). Each style SHALL use the Satoshi font family. Font sizes SHALL be derived from the iOS `AppFonts.getSatoshiX(_ size: Float)` usage patterns.

#### Scenario: Typography applied via Material theme
- **WHEN** a Compose component uses `MaterialTheme.typography.titleLarge`
- **THEN** the rendered text uses Satoshi Medium at the corresponding size

### Requirement: Eight-point spacing grid
The `core/ui` module SHALL define a spacing token set with values from the 8-point grid: `space2 = 4.dp`, `space4 = 8.dp`, `space6 = 12.dp`, `space8 = 16.dp`, `space12 = 24.dp`, `space16 = 32.dp`, `space24 = 48.dp`, `space32 = 64.dp`.

#### Scenario: Components use spacing tokens
- **WHEN** a Compose component sets padding or margin
- **THEN** it references one of the named `spaceN` tokens, not a raw `.dp` literal

### Requirement: Shape tokens defined
The `core/ui` module SHALL define `Shapes` with three corner radii: `extraSmall = 4.dp`, `small = 8.dp`, `medium = 12.dp`, `large = 16.dp`.

#### Scenario: Components use shape tokens
- **WHEN** a Compose component applies rounded corners
- **THEN** it references `MaterialTheme.shapes.small` (or another named shape), not a raw `.dp` literal

### Requirement: Material 3 ColorScheme populated from tokens
`core/ui/theme/Theme.kt` SHALL expose `KornerzTheme` composable that accepts `darkTheme: Boolean` (defaulted to system `isSystemInDarkTheme()`). It SHALL wrap content in `MaterialTheme` with `colorScheme`, `typography`, and `shapes` derived from the Kornerz tokens.

#### Scenario: Compose previews show themed colors
- **WHEN** a `@Preview` composable renders inside `KornerzTheme { ... }`
- **THEN** Kornerz brand colors appear in the preview

#### Scenario: Dark mode preview
- **WHEN** a `@Preview` composable renders inside `KornerzTheme(darkTheme = true) { ... }`
- **THEN** Kornerz dark color scheme colors appear

### Requirement: Tokens exposed via MaterialTheme only
Component code in P1+ MUST consume tokens through `MaterialTheme.colorScheme`, `MaterialTheme.typography`, `MaterialTheme.shapes`. Direct references to `KornerzPurple`, `space8`, etc. from outside `core/ui/theme/` SHALL NOT be allowed.

#### Scenario: No direct token references outside theme
- **WHEN** a Compose component outside `core/ui/theme/` references a brand color
- **THEN** it accesses it via `MaterialTheme.colorScheme.X`, not via a direct color constant

