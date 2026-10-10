## ADDED Requirements

### Requirement: KornerzCarousel tile tap resolves to the Kornerz detail destination
`KornerzCarousel`'s `onItemClick(community: Community)` callback SHALL continue to be invoked when a tile is tapped (this contract is unchanged from P2). Every caller of `KornerzCarousel` (currently Home's "Your Kornerz" and the shared Home carousel row; in P3+ also the Kornerz tab list and Korner detail's Members row may compose it) SHALL route the `onItemClick` callback to the `kornerz/{community.id}` destination inside the Kornerz tab's nested `NavHost`. The shell SHALL no longer emit a `Coming in Phase 3` snackbar for `KornerzCarousel` tile taps in P3+.

#### Scenario: Tile tap navigates to Korner detail
- **WHEN** any caller of `KornerzCarousel` composes it with `onItemClick = { community -> navigateToKornerDetail(community.id) }` and a user taps a tile
- **THEN** the navigation call resolves to the `kornerz/{community.id}` destination inside the Kornerz tab's nested `NavHost`
- **AND** no snackbar is shown

#### Scenario: Caller is responsible for destination wiring
- **WHEN** a future caller composes `KornerzCarousel` without providing a navigation callback
- **THEN** the composable renders normally and tile taps invoke the (possibly no-op) `onItemClick` lambda
- **AND** the composable does not own navigation itself