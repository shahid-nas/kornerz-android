# design-system Specification

## References
- [`docs/architecture.md`](../../../../docs/architecture.md) — shared-component contracts in `core/ui/components/`, the "no magic numbers" rule, `Modifier`-last parameter order, the `@Preview` requirement, and the token-access rule.
- `kornerz/Kornerz/Components/ExpandableText.swift` — iOS source-of-truth for the collapsible text component and its overflow heuristic.
- `kornerz/Kornerz/Components/ExpandableHTMLDescriptionView.swift`, `kornerz/Kornerz/Components/WebViewRepresentable.swift` — iOS source-of-truth for the HTML description component.
- `kornerz/Kornerz/SwiftUI/Components/TriangleImageView.swift`, `kornerz/Kornerz/SwiftUI/Components/TriangleClippedShape.swift` — iOS source-of-truth for the triangle-clipped avatar.
- `kornerz/Kornerz/Extensions/Date+Extension.swift` — iOS source-of-truth for `timeAgoSinceDate()`.
- `kornerz/Kornerz/Backend/Repositories/RandomColorService.swift` — iOS source-of-truth for the five-colour accent palette.
- [`openspec/changes/loop-detail-ios-parity/proposal.md`](../proposal.md) — capability rationale for the iOS parity work.
- [`openspec/changes/loop-detail-ios-parity/design.md`](../design.md) — the shared-component decisions.

## ADDED Requirements

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
