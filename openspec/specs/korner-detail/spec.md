# korner-detail Specification

## Purpose
Defines the Android Korner detail screen (`kornerz/{kornerId}`) and its
parity with the iOS `KornerDetailView`
(`kornerz/Kornerz/SwiftUI/Dashboard/Korner/KornerDetail/KornerDetailView.swift`).

The screen is a **single scrolling surface** with four blocks, in the iOS
order: the hero header, the tabbed "Nooks" section, the tabbed users
section, and the "Recently in &lt;community&gt;" section. The About text is
**not** inline — the header's "About ⌄" affordance presents it as a modal
bottom sheet, mirroring iOS `KornerDetailCoordinator.openAbout`.
## Requirements
### Requirement: Korner detail screen is reachable from the Kornerz tab and from Home
The Korner detail destination `kornerz/{kornerId}` SHALL be reachable from (a) tapping a row in the Kornerz tab list, (b) tapping a tile in the Home tab's "Your Kornerz" carousel, and (c) tapping a tile in the shared `KornerzCarousel` composable. The destination SHALL receive `kornerId` as a nav-arg via `SavedStateHandle`. The destination SHALL be hosted inside the Kornerz tab's nested `NavHost` and SHALL preserve the bottom navigation bar visibility rule from `android-app-shell`.

#### Scenario: Open from Kornerz tab
- **WHEN** the user taps a row in the Kornerz tab list with `kornerId = "abc"`
- **THEN** the Korner detail screen renders for `kornerId = "abc"`
- **AND** the bottom navigation bar remains visible
- **AND** the Kornerz tab remains selected

#### Scenario: Open from Home "Your Kornerz" carousel
- **WHEN** the user taps a tile in the Home tab's "Your Kornerz" carousel with `kornerId = "abc"`
- **THEN** the root NavHost navigates to the Kornerz tab and pushes the `kornerz/{kornerId}` destination for `kornerId = "abc"`
- **AND** the bottom navigation bar remains visible
- **AND** the Kornerz tab is now selected (matching iOS tab-switch behavior in `AppTabController.swift`)

#### Scenario: System back returns to caller
- **WHEN** the user is on the Korner detail screen and presses the system back button
- **THEN** the nested NavHost pops to the `kornerz/list` destination if entered from the Kornerz tab
- **OR** the root NavHost returns to the Home tab if entered from the Home tab

### Requirement: Korner detail header renders the theme-colour hero
The Korner detail screen SHALL render a `KornerDetailHeader` composable at the top, mirroring iOS `SwiftUI/Dashboard/Korner/KornerDetail/KornerDetailHeaderView.swift`. The header SHALL be a solid block in the Korner's theme colour (`korner.themeColor`, falling back to the brand Coral swatch) and SHALL contain, in the iOS arrangement: a back chevron on the leading edge, the Korner name in Satoshi Medium 20sp white beneath it, the Korner cover as a 125-dp triangle-clipped image (15-dp corner radius, top-right corner) on the trailing edge, and a bottom action row holding a Follow / Unfollow mini-capsule (Charcoal `#2E2F3E` fill, white 12sp label) next to an "About" label with a 12-dp chevron. The header SHALL NOT render a 16:9 cover banner with a scrim, nor a full-width Join / Joined button.

#### Scenario: Header renders the hero
- **WHEN** the Korner detail screen renders for a Korner with theme colour `#E27D5A`
- **THEN** the header is a solid `#E27D5A` block
- **AND** the Korner name is shown in white beneath the back chevron
- **AND** the cover is shown as a 125-dp triangle-clipped image on the trailing edge
- **AND** the Follow capsule and the "About ⌄" affordance are shown on the leading edge of the bottom action row

#### Scenario: Follow reflects the joined state
- **WHEN** the signed-in user follows the Korner (`korner.isJoined == true`)
- **THEN** the header's capsule reads "Unfollow"
- **AND** it reads "Follow" otherwise

#### Scenario: Tapping Follow writes the Fellow record
- **WHEN** the user taps the Follow / Unfollow capsule
- **THEN** the screen writes the follow record to the `Fellow` collection, mirroring iOS `FellowRepository.followCommunity` — a document whose id is a random 8-character code, carrying `fellowId` (the same code), `fellowCommunity` (the community), `fellowUserName` (the signed-in user's `codeUser`) and `fellowAtive` (`true`), written with a plain `set` (no merge, no server timestamp)
- **AND** the header's capsule reads "Unfollow" once the write succeeds
- **AND** the About sheet's Follow / Unfollow capsule reflects the same state

#### Scenario: Tapping Unfollow removes the Fellow record
- **WHEN** the signed-in user follows the Korner and taps the Follow / Unfollow capsule
- **THEN** the screen deletes the signed-in user's `Fellow` record for that community, mirroring iOS `FellowRepository.unfollowCommunity`
- **AND** the header's capsule reads "Follow" once the delete succeeds

#### Scenario: A failed follow write reports back
- **WHEN** the follow or unfollow write fails
- **THEN** the screen emits a `ShowMessage` navigation event
- **AND** the capsule keeps its previous Follow / Unfollow state

#### Scenario: Tapping the back chevron pops the screen
- **WHEN** the user taps the header's back chevron
- **THEN** the screen emits `NavigateBack`
- **AND** the shell pops the Korner detail off the Kornerz tab's nested NavHost

### Requirement: Korner detail About is presented as a bottom sheet
The Korner detail screen SHALL NOT render the Korner description inline. The header's "About ⌄" affordance SHALL present a `KornerAboutSheet` modal bottom sheet, mirroring iOS `KornerAboutView` presented by `KornerDetailCoordinator.openAbout` (a sheet with `.medium()` / `.large()` detents). The sheet SHALL contain the Korner name in Satoshi Bold 16sp, a "Description" label in Satoshi Bold 15sp, the description body in Satoshi Regular 16sp, and a full-width Follow / Unfollow primary capsule (brand purple fill, white label) pinned to the bottom. Tapping "About" SHALL open the sheet and dismissing the sheet SHALL close it.

#### Scenario: About opens from the header
- **WHEN** the user taps the header's "About ⌄" affordance
- **THEN** the About bottom sheet is presented
- **AND** the sheet shows the Korner name, the "Description" label and the description text

#### Scenario: About is not inline
- **WHEN** the Korner detail screen renders
- **THEN** no description section is rendered between the header and the Nooks section

### Requirement: Korner detail Nooks section renders a tabbed carousel
The Korner detail screen SHALL render a `KornerDetailNooksSection` composable, mirroring iOS `SwiftUI/Dashboard/Korner/Sections/KornerDetailNooksView.swift`. The section SHALL render a tab strip with a "Nooks" title (Satoshi Bold 20sp) and two tabs, "Current" and "Upcoming", with the selected tab marked by a 2-dp underline in the sand swatch. The "Current" tab SHALL render a single card for the Korner's live nook; the "Upcoming" tab SHALL render a horizontally scrolling carousel of the Korner's scheduled nooks, each card 70% of the screen width and 100 dp tall. Both cards SHALL be painted in the Korner's theme colour with a 12-dp corner radius, matching iOS `KornerCurrentNookView` and `KornerNookView`. The "Upcoming" tab SHALL show a "No upcoming nooks" message when there are no scheduled nooks. Tapping a card SHALL emit `KornerDetailNavEvent.NavigateToNookDetail(nookId)`.

#### Scenario: Current tab shows the live nook
- **WHEN** the Korner detail screen renders for a Korner with a live nook
- **AND** the "Current" tab is selected
- **THEN** a single card in the Korner's theme colour is shown beneath the tab strip
- **AND** the card is tappable and emits `NavigateToNookDetail(nookId)`

#### Scenario: Upcoming tab shows a carousel
- **WHEN** the "Upcoming" tab is selected
- **THEN** the scheduled nooks are shown in a horizontal carousel
- **AND** each card is 70% of the screen width and 100 dp tall

#### Scenario: Upcoming tab is empty
- **WHEN** the "Upcoming" tab is selected and the Korner has no scheduled nooks
- **THEN** the section shows "No upcoming nooks"

### Requirement: Korner detail users section renders Kornerstones and Klouders tabs
The Korner detail screen SHALL render a `KornerDetailUsersSection` composable, mirroring iOS `SwiftUI/Dashboard/Korner/Sections/KornerUsersView.swift`. The section SHALL render a tab strip whose title is the selected tab's title, with two tabs, "Kornerstones" and "Klouders", the selected tab marked by a 2-dp underline in the sand swatch. The "Kornerstones" tab SHALL show the Korner's **admins** as 40-dp triangle-clipped avatars with an 8-dp corner radius; the "Klouders" tab SHALL show the Korner's **followers** as 40-dp diamond avatars with an 8-dp corner radius. Each avatar SHALL carry the user's nickname beneath it in Satoshi Regular 10sp, on one line. An empty tab SHALL show a "No &lt;tab&gt; found" message in Satoshi Bold 14sp. Tapping an avatar SHALL emit `KornerDetailNavEvent.NavigateToMemberProfile(userId)`.

#### Scenario: Kornerstones shows the admins
- **WHEN** the Korner detail screen renders for a Korner with admins
- **AND** the "Kornerstones" tab is selected
- **THEN** the admins are shown as triangle-clipped 40-dp avatars with their nicknames beneath

#### Scenario: Klouders shows the followers
- **WHEN** the "Klouders" tab is selected
- **THEN** the Korner's followers are shown as diamond 40-dp avatars with their nicknames beneath

#### Scenario: An empty tab says so
- **WHEN** the selected tab has no users
- **THEN** the section shows "No kornerstones found" or "No klouders found"

### Requirement: Korner detail renders the "Recently in <community>" section
The Korner detail screen SHALL render a "Recently in &lt;community&gt;" block beneath the users section, mirroring iOS `KornerDetailView.recentHeader` + `recentContents`. The block SHALL be emitted as items of the screen's own `LazyColumn` — via the `KornerDetailRecentHeader` composable for the header and the loop rows as list items — because a vertically scrollable component nested inside another vertically scrolling container is measured with an infinite height constraint. The rows SHALL reuse the shared `LoopItemView` chrome from `feature/loops` and be painted in the per-index accent palette (`KornerzCardPalette.color(index)`). When the Korner has no loops the block SHALL show a 48-dp grey tray glyph above a 16-sp medium grey "No loops available" message. Tapping a row SHALL emit `KornerDetailNavEvent.NavigateToLoopDetail(loopId, accentColorHex)`, which opens the full-screen loop detail in the very colour the tapped row was painted with (iOS hands `loopInfo.color` to the loop detail).

#### Scenario: Recently in lists the Korner's loops
- **WHEN** the Korner detail screen renders for a Korner with loops
- **THEN** a "Recently in &lt;community&gt;" header is shown above the loop rows
- **AND** tapping a row emits `NavigateToLoopDetail(loopId, accentColorHex)` with the row's accent colour

#### Scenario: Recently in is empty
- **WHEN** the Korner has no loops
- **THEN** the section shows the tray glyph and "No loops available"

### Requirement: Korner detail supports pull-to-refresh and a shimmer that mirrors the layout
The Korner detail screen SHALL wrap its content in a Material 3 `PullToRefreshBox`. Pulling SHALL re-read the Korner, its admins and its loops while keeping the current content on screen. While the first load is in flight the screen SHALL render a shimmer that mirrors the settled layout — the hero block, a section title and three row placeholders — so nothing jumps when the data lands.

#### Scenario: Pull to refresh re-reads the Korner
- **WHEN** the user pulls the screen down
- **THEN** the refresh indicator appears
- **AND** the Korner, its admins and its loops are re-read
- **AND** the content stays on screen until the read resolves

#### Scenario: Initial load shows the shimmer
- **WHEN** the Korner detail screen opens for a Korner that has not loaded yet
- **THEN** a shimmer in the settled layout's shape is shown

### Requirement: Korner detail view model exposes AsyncValue state and navigation events
`KornerDetailViewModel` SHALL be a `@HiltViewModel` constructed with a `SavedStateHandle` that exposes `kornerId: String`. It SHALL expose `state: StateFlow<KornerDetailState>` where `KornerDetailState` contains an `AsyncValue<KornerDetail>` aggregate, the Korner's admins, the Korner's recent loops, the selected nook tab, the selected users tab and the About sheet's visibility; and a `navEvents: Channel<KornerDetailNavEvent>` for navigation. `KornerDetailNavEvent.NavigateToNookDetail(nookId)`, `NavigateToMemberProfile(userId)`, `NavigateToLoopDetail(loopId)`, `NavigateBack` and `ShowMessage(message)` SHALL be emitted on the matching interactions. The view model SHALL initiate `KornerzRepository.fetchKornerDetail(kornerId)` on construction and SHALL then load the Korner's admins (`fetchKornerAdmins`) and loops (`fetchKornerRecentLoops`).

#### Scenario: Initial composition triggers fetch
- **WHEN** the Korner detail composable first enters the composition with `kornerId = "abc"`
- **THEN** `KornerDetailViewModel` initiates `fetchKornerDetail("abc")`
- **AND** `state` transitions from `AsyncValue.Uninitialized` to `AsyncValue.Loading`

#### Scenario: Successful fetch populates state
- **WHEN** `fetchKornerDetail("abc")` resolves with a non-null `KornerDetail`
- **THEN** `state` transitions to `AsyncValue.Success(kornerDetail)`
- **AND** the admins and the recent loops are fetched alongside it

#### Scenario: Failed fetch renders retry
- **WHEN** `fetchKornerDetail("abc")` resolves to an error
- **THEN** `state` transitions to `AsyncValue.Error`
- **AND** the screen renders a retry view with a "Try again" button that invokes `KornerDetailViewModel.refresh()`

### Requirement: Korner detail derives the follow state from the Fellow collection
The Korner detail read SHALL derive `korner.isJoined` from the signed-in
user's own `Fellow` record for the Korner, mirroring iOS
`KornerDetailViewModel.checkIfFellowExists`. The check SHALL be a dedicated
existence query against the `Fellow` collection, NOT a scan of the
Korner's member list — that list is capped at 50 entries and therefore may
not contain the signed-in user's own record. The community SHALL be matched
by every key it is known by (`communityId`, `communityName`,
`communityNameEn`) so that a follow written by either iOS path is
recognised.

#### Scenario: The follow state comes from a dedicated existence query
- **WHEN** the Korner detail is read for the signed-in user
- **THEN** the follow state is resolved by querying the `Fellow` collection for a record whose `fellowUserName` is the signed-in user's `codeUser` and whose `fellowCommunity` matches one of the Korner's community keys
- **AND** the query runs alongside the Korner's nook and member reads

#### Scenario: A follow written on iOS is recognised
- **WHEN** the signed-in user follows the Korner on the iOS app and then opens the Korner detail on Android
- **THEN** `korner.isJoined` is `true`
- **AND** the header's capsule reads "Unfollow"

