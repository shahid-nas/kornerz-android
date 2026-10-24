# notifications-list Specification

## Purpose
TBD - created by archiving change phase-6-profile-and-notifications. Update Purpose after archive.
## Requirements
### Requirement: Notifications screen renders paginated list of UserNotification items
`NotificationsScreen` SHALL render a `LazyColumn` of `NotificationItem`s ordered by `dateNotification` descending. Each item SHALL show: a 40-pt circular avatar (Coil-loaded from `UserNotification.thumbnailURL` with `R.drawable.ic_community_placeholder` fallback), the community/content text in 14-pt `colorScheme.onSurfaceVariant`, the date string in 10-pt `colorScheme.onSurfaceVariant`, and the notification description in Satoshi Black 16-pt `colorScheme.onSurface`. Each item SHALL be wrapped in a rounded 20-pt card with `colorScheme.surfaceVariant` background and a 1-px `colorScheme.outlineVariant` stroke. Mirrors iOS `NotificationScreen.swift` lines 76–125 (`NotificationItem` view).

#### Scenario: First page render
- **WHEN** `NotificationsViewModel` exposes `NotificationsState.Success(items = firstPage)` after `fetchNotifications` returns the first 10 items
- **THEN** the `LazyColumn` renders 10 cards in `dateNotification` descending order

#### Scenario: Pagination triggers on last item appearance
- **WHEN** the last visible `NotificationItem.id == NotificationsState.items.last.id`
- **THEN** `NotificationsViewModel.fetchNextPage()` is invoked and the second page is appended

#### Scenario: Empty state
- **WHEN** `NotificationsState.items.isEmpty() && !isLoading && error == null`
- **THEN** `KornerzEmptyState(title = "No notifications")` is rendered (mirrors iOS `NoDataView` line 29)

#### Scenario: Error state with retry
- **WHEN** `fetchNotifications` returns a failure on the first page
- **THEN** `KornerzErrorState(error)` with a Retry button is rendered (mirrors iOS `RetryView` line 24). Tapping Retry re-invokes `fetchNotifications`.

#### Scenario: Loading state on first page
- **WHEN** `NotificationsState.items.isEmpty() && isLoading = true`
- **THEN** a shimmer placeholder is rendered (mirrors iOS `shimmerView` line 71)

#### Scenario: Pagination footer loading
- **WHEN** `isLoadingMore = true` (loading subsequent page)
- **THEN** a footer shimmer (`LoadingMoreView`) is rendered below the last item (mirrors iOS line 57)

### Requirement: Fetch uses Firestore startAfter cursor with limit 10
`NotificationRepository.fetchNotifications(userCode, after)` SHALL build `db.collection("Notifications").whereEqualTo("notificationUserId", userCode).orderBy("dateNotification", Query.Direction.DESCENDING).limit(10)` and apply `startAfter(latestPost)` when `after != null`. Mirrors iOS `NotificationRepository.fetchNotifications` lines 40–69. The return shape SHALL be `Result<Pair<List<UserNotification>, DocumentSnapshot?>>` where the snapshot is the last document in the page (used as the next `after` argument).

#### Scenario: First page has no cursor
- **WHEN** `fetchNotifications(userCode, after = null)`
- **THEN** the repository returns the first 10 documents + a snapshot for the 10th

#### Scenario: Subsequent page has cursor
- **WHEN** `fetchNotifications(userCode, after = snapshotFromPreviousPage)`
- **THEN** the repository returns the next 10 documents starting after the snapshot

#### Scenario: Empty page marks end of pagination
- **WHEN** `fetchNotifications` returns an empty list (fewer than 10 documents left)
- **THEN** `NotificationsViewModel.hasMoreData = false` and no further `fetchNextPage()` is invoked

### Requirement: Pull-to-refresh restarts pagination
Pulling down on the `LazyColumn` SHALL invoke `NotificationsViewModel.forcedRefresh()` which clears the cached items + cursor and re-invokes `fetchNotifications(userCode, after = null)`. Mirrors iOS `.refreshable { viewModel.forcedRefresh() }` lines 65–67.

#### Scenario: Pull-to-refresh reloads first page
- **WHEN** user pulls down on the notifications list
- **THEN** the cursor is cleared, `fetchNotifications(userCode, after = null)` is invoked, and the list reloads with the latest 10 items

### Requirement: Tapping a notification opens its deeplink or fallback alert
Tapping a `NotificationItem` SHALL invoke `NotificationsViewModel.onNotificationOpened(notification)`. The ViewModel SHALL:
1. If `notification.deeplinkType` is `NewLoop` or `NewLoopComment` and `notification.reference != null`, emit `NotificationsNavEvent.OpenLoopComment(loopId = reference)`.
2. If `notification.deeplinkType` is `NewComment` and `notification.reference != null`, emit `NotificationsNavEvent.OpenForumComment(forumId = reference)`.
3. Otherwise fetch the `Community` (by `communityId` if present, else by `content` name) and resolve the deeplink via `UserNotification.deeplink(with: Community)`. If a deeplink resolves, emit `NotificationsNavEvent.Open(deeplink)` carrying the typed `DeepLink`. If the Community lookup fails or no deeplink resolves, emit `NotificationsNavEvent.ShowAlert(message = "Community not found")`.

The `Open(deeplink)` event SHALL be handled by the shell by routing the deep link through the `DeepLinkResolver` (P10, `deep-link-routing` capability) instead of showing a placeholder. A deep link whose target screen is excluded from the Android port SHALL surface the shared "Failed to load deeplink. Will navigate back to home screen" alert and return the user to the Home tab.

The `"Deeplink coming in Phase 10"` placeholder SHALL be removed.

Mirrors iOS `NotificationsViewModel.notificationOpened` lines 121–151.

#### Scenario: Loop-comment tap opens the loop
- **WHEN** user taps an item with `deeplinkType = NewLoopComment` and `reference = "loop-123"`
- **THEN** `NotificationsNavEvent.OpenLoopComment(loopId = "loop-123")` is emitted and the shell routes to `loops/loop-123`

#### Scenario: A new-loop tap opens the loop
- **WHEN** user taps an item with `deeplinkType = NewLoop` and `reference = "loop-123"`
- **THEN** `NotificationsNavEvent.OpenLoopComment(loopId = "loop-123")` is emitted and the shell routes to `loops/loop-123`

#### Scenario: A video-call tap routes to the Korner detail
- **WHEN** user taps an item with `deeplinkType = VideoCall` and a resolvable community
- **THEN** a typed video-call deep link is emitted and the shell routes to the Korner detail for that community

#### Scenario: A tap on an excluded target surfaces the alert
- **WHEN** user taps an item whose type targets a screen excluded from the Android port (Event Detail, Post Detail, Forum Detail)
- **THEN** the shared "Failed to load deeplink. Will navigate back to home screen" alert is shown and the user is returned to the Home tab

#### Scenario: A missing community surfaces the alert
- **WHEN** user taps an item whose community cannot be resolved
- **THEN** `NotificationsNavEvent.ShowAlert(message = "Community not found")` is emitted

#### Scenario: No placeholder snackbar remains
- **WHEN** the notifications screen's deep-link handler is inspected
- **THEN** it contains no "Deeplink coming in Phase 10" message

