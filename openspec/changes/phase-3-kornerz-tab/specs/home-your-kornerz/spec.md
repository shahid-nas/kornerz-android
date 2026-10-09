## ADDED Requirements

### Requirement: Home Your Kornerz tile tap resolves to the Kornerz detail destination
The Home tab's `HomeViewModel` SHALL emit `HomeNavEvent.NavigateToKornerDetail(kornerId)` when a tile in the "Your Kornerz" carousel is tapped (this emission is unchanged from P2). The shell's nav-event handler SHALL resolve `HomeNavEvent.NavigateToKornerDetail(kornerId)` by switching to the Kornerz tab and pushing the `kornerz/{kornerId}` destination inside the Kornerz tab's nested `NavHost`. The shell SHALL no longer emit a `Coming in Phase 3` snackbar for this event.

#### Scenario: Tap a Your Kornerz tile navigates to Korner detail
- **WHEN** the user taps a tile in the Home tab's "Your Kornerz" carousel with `kornerId = "abc"`
- **THEN** the root NavHost switches to the Kornerz tab
- **AND** the Kornerz tab's nested NavHost pushes the `kornerz/abc` destination
- **AND** the bottom navigation bar remains visible
- **AND** no snackbar is shown

#### Scenario: No more Phase 3 placeholder snackbar from Home
- **WHEN** any `HomeNavEvent.NavigateToKornerDetail(kornerId)` event is observed by the shell
- **THEN** the shell routes to the real `kornerz/{kornerId}` destination
- **AND** the user never sees the `Coming in Phase 3` snackbar from the Home carousel in P3+