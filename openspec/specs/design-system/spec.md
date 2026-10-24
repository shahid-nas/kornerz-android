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

### Requirement: KornerzDiamondImage shared component
The `core/ui/components/` module SHALL provide a `KornerzDiamondImage` Composable that renders an image clipped into a 45°-rotated rounded square (1:1 port of iOS `DiamondImageView`). The Composable SHALL accept `imageUrl: String?`, `fallbackAsset: String?`, `size: Dp`, `cornerRadius: Dp`, `modifier: Modifier = Modifier` (last per arch §4). When `imageUrl` is null or fails to load, the Composable SHALL fall back to the supplied asset (or a default "user" mark).

#### Scenario: Diamond renders URL image
- **WHEN** `imageUrl` is non-null and Coil loads it successfully
- **THEN** the image is rendered clipped into the diamond shape at the requested size

#### Scenario: Diamond falls back to asset
- **WHEN** `imageUrl` is null or Coil fails
- **THEN** the fallback asset is rendered inside the same diamond shape

### Requirement: HomeShimmerPlaceholder shared component
The `core/ui/components/` module SHALL provide a `HomeShimmerPlaceholder` Composable that wraps any child in a shimmer effect (mirrors iOS `.redacted(reason: .placeholder)`). The Composable SHALL accept `modifier: Modifier = Modifier` (last) and a content lambda.

#### Scenario: Shimmer placeholder wraps content
- **WHEN** a section renders `HomeShimmerPlaceholder { ShimmerContent() }`
- **THEN** the shimmer overlay animates over `ShimmerContent()`

### Requirement: The shared component set gains the three iOS-named state views
`core/ui/components/` SHALL expose three additional shared components introduced in P10, each mirroring its iOS counterpart's structure:

| Component | iOS counterpart | Structural contract |
|---|---|---|
| `KornerzRetryView` | `kornerz/Kornerz/Components/RetryView.swift` | centred 12-pt error message above a grey-tinted capsule button labelled `Retry again` |
| `KornerzNoDataView` | `kornerz/Kornerz/Components/NoDataView.swift` | a 75 × 75 dp illustration above a title in the Kornerz body type, with 50 dp of vertical padding |
| `KornerzLoadingMoreView` | `kornerz/Kornerz/Components/LoadingMoreView.swift` | a centred, full-width row with a progress indicator and, 16 dp beside it, the label `Loading more items` |

These components join `KornerzEmptyState`, `KornerzErrorState`, `KornerzLoadingView`, `KornerzButton`, `KornerzTextField`, `KornerzPasswordField`, `KornerzAvatar`, `KornerzDiamondImage`, and `HomeShimmerPlaceholder` in the shared set. The `app-polish` capability holds the behavioural requirements; this requirement records the components' membership in the design system.

The three new components SHALL NOT duplicate an existing shared component's job: `KornerzEmptyState` (generic empty state) and `KornerzNoDataView` (the iOS-named empty state) SHALL both remain available, and `KornerzErrorState` SHALL remain available alongside `KornerzRetryView`.

Every new component SHALL be themed from `KornerzColors` and `KornerzTypography`. No component SHALL hard-code a hex colour.

#### Scenario: The three components exist
- **WHEN** `core/ui/components/` is enumerated
- **THEN** `KornerzRetryView`, `KornerzNoDataView`, and `KornerzLoadingMoreView` are all present

#### Scenario: The components are themed, not hard-coded
- **WHEN** the three components are inspected
- **THEN** none of them declares a literal hex colour

#### Scenario: The existing components are not removed
- **WHEN** `core/ui/components/` is enumerated
- **THEN** `KornerzEmptyState`, `KornerzErrorState`, and `KornerzLoadingView` are all still present

### Requirement: KornerzExpandableText shared component
The `core/ui/components/` module SHALL provide a `KornerzExpandableText` Composable — the 1:1 Android counterpart of iOS `Components/ExpandableText.swift` — that renders a body of text collapsed to a line limit with an inline toggle. The Composable SHALL accept `text: String`, `lineLimit: Int = 2`, `accentColor: Color`, `modifier: Modifier = Modifier` (last, per `docs/architecture.md` §4), and `expandedLabel: String = "View More"` / `collapsedLabel: String = "View Less"`.

The toggle SHALL render only when the text overflows the limit, and the overflow test SHALL be iOS's heuristic: `text.length / 50 + newlineCount > lineLimit`. The toggle SHALL be tinted with `accentColor` and SHALL animate the expansion over 300 ms with an ease-in-out easing. Tapping the toggle SHALL flip the expansion state **within the component's own state** — the component SHALL NOT require the caller to hoist the expansion flag.

#### Scenario: Overflowing text renders a toggle
- **WHEN** a 120-character single-line body is rendered with `lineLimit = 2`
- **THEN** the text is truncated to 2 lines
- **AND** a "View More" toggle tinted with `accentColor` renders

#### Scenario: Short text renders no toggle
- **WHEN** a 40-character single-line body is rendered with `lineLimit = 2`
- **THEN** no toggle renders
- **AND** the text renders in full

#### Scenario: The overflow heuristic counts newlines
- **WHEN** a 90-character body containing 2 newlines is rendered with `lineLimit = 2`
- **THEN** a toggle renders (90 / 50 + 2 = 3, which is greater than 2)

#### Scenario: Toggling expands and collapses the body
- **WHEN** the user taps the toggle
- **THEN** the body renders at its full height with no line limit
- **AND** the toggle label becomes "View Less"
- **WHEN** the user taps the toggle again
- **THEN** the body collapses back to the line limit

### Requirement: KornerzHtmlText shared component
The `core/ui/components/` module SHALL provide a `KornerzHtmlText` Composable — the 1:1 Android counterpart of iOS `Components/ExpandableHTMLDescriptionView.swift` together with `Components/WebViewRepresentable.swift` — that renders an HTML fragment. The Composable SHALL accept `html: String`, `accentColor: Color`, `modifier: Modifier = Modifier` (last), `maxExpandedHeight: Dp = 200.dp`, and `collapsedLineLimit: Int = 2`.

When collapsed, the component SHALL render a **plain-text** preview of the HTML (tags stripped) truncated to `collapsedLineLimit` lines, followed by a **"See More"** toggle tinted with `accentColor`. When expanded, the component SHALL host a `WebView` through `AndroidView` whose height is driven by the content height reported back from the view, clamped to `maxExpandedHeight`, and the toggle SHALL read **"Show Less"**.

The hosted `WebView` SHALL have file access disabled (`settings.allowFileAccess = false`), SHALL disable JavaScript-driven navigation away from the fragment, and SHALL intercept URL clicks via `shouldOverrideUrlLoading` returning `true` so no external page is loaded in-place. The component SHALL NOT require the caller to hoist the expansion flag.

#### Scenario: Collapsed HTML renders a plain-text preview
- **WHEN** a `KornerzHtmlText` is rendered with `html = "<p>Hello <b>world</b></p>"`
- **THEN** the collapsed state renders the tag-stripped text "Hello world" truncated to 2 lines
- **AND** a "See More" toggle tinted with `accentColor` renders
- **AND** no `WebView` is attached

#### Scenario: Expanded HTML renders the WebView
- **WHEN** the user taps "See More"
- **THEN** a `WebView` is attached and renders the HTML fragment
- **AND** the toggle label becomes "Show Less"
- **AND** the hosted height is clamped to `maxExpandedHeight`

#### Scenario: The WebView is locked down
- **WHEN** the `WebView` is attached
- **THEN** `settings.allowFileAccess` is `false`
- **AND** `shouldOverrideUrlLoading` returns `true` for every URL

### Requirement: KornerzTriangleImage shared component
The `core/ui/components/` module SHALL provide a `KornerzTriangleImage` Composable — the 1:1 Android counterpart of iOS `SwiftUI/Components/TriangleImageView.swift` together with `TriangleClippedShape.swift` — that renders an image clipped into a triangle with a rounded corner. The Composable SHALL accept `imageUrl: String?`, `size: Dp`, `cornerRadius: Dp = 8.dp`, `modifier: Modifier = Modifier` (last), and SHALL expose the triangle corner through a `TriangleCorner` enum with the four cases `TopRight`, `TopLeft`, `BottomRight`, `BottomLeft` — mirroring the existing `TriangleClippedShape` in `core/ui/components/`.

When `imageUrl` is null or fails to load, the Composable SHALL render a neutral placeholder surface inside the same clipped shape. The component SHALL be built on the existing `TriangleClippedShape` rather than introducing a second clipping implementation.

#### Scenario: Triangle renders a URL image
- **WHEN** `imageUrl` is non-null and Coil loads it successfully
- **THEN** the image renders clipped into the triangle shape at the requested size

#### Scenario: Triangle falls back to a placeholder
- **WHEN** `imageUrl` is null or Coil fails
- **THEN** a neutral placeholder surface renders inside the same triangle shape

#### Scenario: The triangle corner is selectable
- **WHEN** the component is rendered with the `BottomLeft` corner
- **THEN** the clipped shape clips the opposite corner, matching iOS `TriangleImageView`'s `.bottomLeft` behaviour

### Requirement: KornerzTimeAgo shared formatter
The `core/ui/components/` module SHALL provide a `timeAgoSinceDate(instant: kotlinx.datetime.Instant?, now: kotlinx.datetime.Instant = Clock.System.now()): String` formatter — the 1:1 Android counterpart of iOS `Extensions/Date+Extension.swift`'s `timeAgoSinceDate()`. The formatter SHALL render the largest fitting unit from **seconds, minutes, hours, days, weeks, months, years**, and SHALL render an empty string for a null instant.

The formatter SHALL be the single relative-time implementation in the app: the loops module's private `formatTimeAgo` helper SHALL be removed and replaced by this one, so the loop detail title row and the comment rows cannot disagree about how a timestamp reads.

#### Scenario: Recent timestamps render in seconds and minutes
- **WHEN** the instant is 30 seconds before `now`
- **THEN** the formatter renders a seconds-granularity string
- **WHEN** the instant is 5 minutes before `now`
- **THEN** the formatter renders a minutes-granularity string

#### Scenario: Older timestamps render in days, weeks, months, and years
- **WHEN** the instant is 3 days before `now`
- **THEN** the formatter renders a days-granularity string
- **WHEN** the instant is 3 weeks before `now`
- **THEN** the formatter renders a weeks-granularity string
- **WHEN** the instant is 3 months before `now`
- **THEN** the formatter renders a months-granularity string
- **WHEN** the instant is 3 years before `now`
- **THEN** the formatter renders a years-granularity string

#### Scenario: A null instant renders an empty string
- **WHEN** the instant is null
- **THEN** the formatter returns an empty string

### Requirement: A single iOS accent palette drives every per-loop colour
`core/ui` SHALL expose the iOS per-loop accent palette as the single source of truth: the five colours `#E27D5A`, `#AD598D`, `#86BFA9`, `#68B6CC`, `#EBAA4A`, cycled by an index — the 1:1 counterpart of iOS `RandomColorService.color(for:)`. `core/ui/theme/KornerzCardPalette.kt` SHALL own the palette, and `KornerzCardPalette.color(index)` SHALL return the colour for an index, wrapping modulo the palette size.

The loops module's `RandomColorService` — which carries a different, eight-colour palette — SHALL be removed, and every per-loop accent colour on the Android side SHALL come from `KornerzCardPalette`. The per-loop colour SHALL be chosen **deterministically from the loop id**, so the same loop always renders in the same colour.

#### Scenario: The palette carries the five iOS colours
- **WHEN** `KornerzCardPalette.color(index)` is called for indices 0 through 4
- **THEN** it returns `#E27D5A`, `#AD598D`, `#86BFA9`, `#68B6CC`, and `#EBAA4A` in that order

#### Scenario: The palette wraps
- **WHEN** `KornerzCardPalette.color(index)` is called with an index of 5
- **THEN** it returns the colour at index 0
- **WHEN** it is called with a negative index
- **THEN** it returns a colour from the palette without throwing

#### Scenario: The loops module no longer owns a second palette
- **WHEN** the loops module is inspected
- **THEN** it contains no `RandomColorService` object
- **AND** every per-loop accent colour it renders comes from `KornerzCardPalette`

### Requirement: The iOS close affordance is available as a vector drawable
`core/ui` SHALL ship an Android vector drawable that is the 1:1 port of the iOS `icon_back` asset (`kornerz/Kornerz/Resources/Assets.xcassets/Home/icon_back.imageset/icon_back.svg`) — a `#2E2F3E` filled circle with a white X. The drawable SHALL be usable as a 24dp and 30dp action-bar icon without a raster source.

#### Scenario: The drawable renders a dark circle with a white X
- **WHEN** the drawable is inflated
- **THEN** it renders a filled `#2E2F3E` circle
- **AND** two white strokes cross inside the circle

