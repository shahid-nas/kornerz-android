# deep-link-routing Specification

## Purpose
TBD - created by archiving change phase-10-ranking-deeplinks-polish. Update Purpose after archive.
## Requirements
### Requirement: A typed DeepLink model mirrors the iOS deep-link cases
`core/model` SHALL declare a `DeepLink` sealed interface with exactly seven cases, mirroring iOS `DeepLinks/DeeplinkManager.swift:13-54` case-for-case:

| Case | Payload | iOS counterpart |
|---|---|---|
| `NewEvent` | `eventId`, `communityId` | `.newEvent(EventLink)` |
| `NewArticle` | `articleId`, `communityId` | `.newArticle(ArticleLink)` |
| `NewForum` | `forumId`, `communityId` | `.newForum(ForumLink)` |
| `VideoCall` | `communityId` | `.videoCall(CallLink)` |
| `NewComment` | `forumId` | `.newComment(CommentLink)` |
| `NewLoop` | `communityId`, `loopId` | `.newLoop(LoopLink)` |
| `NewLoopComment` | `loopId` | `.newLoopComment(LoopCommentLink)` |

The `communityId` carried by a `DeepLink` SHALL be the community's **`communityId` field** — iOS's public community identifier — not the Firestore document id. iOS resolves that field to a document in `DeepLinksManager.handleCallLink` → `getCommunityDetailsFor` (`APICommunity.swift:475`).

`DeepLink` SHALL expose `toUri(): String` producing a `kornerz://` URI, and a companion `parse(uri: String): DeepLink?` producing the typed model back from that URI.

#### Scenario: The seven cases exist
- **WHEN** the `DeepLink` sealed interface is enumerated
- **THEN** it has exactly `NewEvent`, `NewArticle`, `NewForum`, `VideoCall`, `NewComment`, `NewLoop`, and `NewLoopComment`

#### Scenario: A deep link round-trips through its URI
- **WHEN** a `DeepLink.VideoCall("community-1")` is converted to a URI and parsed back
- **THEN** the parsed value is equal to the original

#### Scenario: An unparseable URI yields null
- **WHEN** `DeepLink.parse("https://example.com/loop/1")` is called
- **THEN** it returns `null` and does not throw

### Requirement: The kornerz URI grammar is the transport form of a DeepLink
The `kornerz://` URI grammar SHALL be:

```
kornerz://loop/{loopId}                        → NewLoop / NewLoopComment
kornerz://forum/{forumId}                      → NewComment
kornerz://community/{communityId}/call         → VideoCall
kornerz://community/{communityId}/event/{id}   → NewEvent
kornerz://community/{communityId}/article/{id} → NewArticle
kornerz://community/{communityId}/forum/{id}   → NewForum
```

`kornerz://loop/{loopId}` SHALL map to both `NewLoop` and `NewLoopComment`; the two cases resolve to the same destination, so the collapse is lossless for routing purposes and is carried over from the existing Android implementation.

A URI that does not match the grammar SHALL parse to `null`.

#### Scenario: Loop URIs parse
- **WHEN** `kornerz://loop/loop-1` is parsed
- **THEN** it yields a loop deep link carrying `loopId = "loop-1"`

#### Scenario: Community call URIs parse
- **WHEN** `kornerz://community/community-1/call` is parsed
- **THEN** it yields a `VideoCall` carrying `communityId = "community-1"`

#### Scenario: Community article URIs parse
- **WHEN** `kornerz://community/community-1/article/article-9` is parsed
- **THEN** it yields a `NewArticle` carrying `articleId = "article-9"` and `communityId = "community-1"`

#### Scenario: A malformed URI does not parse
- **WHEN** `kornerz://community/community-1/unknown` is parsed
- **THEN** it yields `null`

### Requirement: The app registers the kornerz scheme
`app/src/main/AndroidManifest.xml` SHALL declare an `<intent-filter>` on `MainActivity` for the `kornerz` scheme covering the `loop`, `forum`, and `community` hosts, so an externally fired `kornerz://` URI reaches the app.

`MainActivity` SHALL declare `android:launchMode="singleTop"`, so a warm deep link is delivered to the existing activity through `onNewIntent` instead of creating a second activity instance.

#### Scenario: The scheme is registered
- **WHEN** the merged manifest of the `devDebug` variant is inspected
- **THEN** `MainActivity` declares an intent filter with the `kornerz` scheme and the `loop`, `forum`, and `community` hosts

#### Scenario: A warm deep link does not duplicate the activity
- **WHEN** a `kornerz://` URI is fired while the app is already running
- **THEN** `MainActivity.onNewIntent` receives it and no second activity instance is created

### Requirement: DeepLinkManager holds links until the app can route them
`feature/shell` SHALL declare a `@Singleton` `DeepLinkManager` with iOS `DeepLinksManager`'s responsibilities (`DeepLinks/DeeplinkManager.swift:66-145`):

| Member | Behaviour |
|---|---|
| `handle(deeplink: DeepLink)` | Accepts a link. Before the app is ready the link is queued; afterwards it is emitted. |
| `notifyAppLaunch()` | Marks the app ready and releases every queued link. |
| `pending` queue | A FIFO queue of links not yet taken by the interstitial. |

The manager SHALL hold links in memory only. A link that arrives before the shell graph exists — a cold start from a deep link — SHALL be queued and released once the shell is composed.

A second link arriving while the first is still being routed SHALL be queued behind it and SHALL NOT overwrite it.

#### Scenario: A cold-start link is queued
- **WHEN** `handle` is called before `notifyAppLaunch`
- **THEN** no link is emitted and the link sits in the queue

#### Scenario: The queue is released on app launch
- **WHEN** `notifyAppLaunch` is called after a link was queued
- **THEN** the queued link is emitted in the order it was queued

#### Scenario: A warm link is emitted immediately
- **WHEN** `handle` is called after `notifyAppLaunch`
- **THEN** the link is emitted without waiting

#### Scenario: Queue order is preserved
- **WHEN** two links are queued before `notifyAppLaunch`
- **THEN** they are emitted in the order they were queued

### Requirement: The deep-link interstitial resolves a link and then removes itself
The deep-link interstitial (13.1) SHALL be a top-level `deeplink/resolving` route on the shell's root `NavHost`, registered in `feature/shell`. It SHALL hide the bottom bar.

The interstitial SHALL render a resolving state, take the head of the `DeepLinkManager`'s queue, and ask the `DeepLinkResolver` whether the link is routable:

- When the link **is** routable, the interstitial SHALL navigate to the resolved destination and SHALL remove itself from the back stack — mirroring iOS `DeeplinkViewController`, which pushes the destination and then calls `removeSelfFromNavigation()` (`DeepLinks/DeeplinkViewController.swift:41-49`).
- When the link is **not** routable, the interstitial SHALL show the alert "Failed to load deeplink. Will navigate back to home screen" (the text of iOS `DeeplinkViewController.showAlert`, `:51-56`) and SHALL return the user to the Home tab.

The interstitial SHALL NOT fetch the linked entity itself. Android's destination screens take an id in their route and load their own data, so the destination's own loading and error states are the single failure surface.

The interstitial route SHALL carry **no** navigation argument. The link is handed to it through the `DeepLinkManager`, so no id has to be encoded into the route.

#### Scenario: A routable link navigates to its destination
- **WHEN** a `kornerz://loop/loop-1` link is routed
- **THEN** the shell navigates to the Loops tab's loop detail for `loop-1`

#### Scenario: The interstitial removes itself
- **WHEN** the interstitial resolves a routable link
- **THEN** the `deeplink/resolving` destination is no longer on the back stack

#### Scenario: An unroutable link shows the alert
- **WHEN** a link whose target screen is excluded from the Android port is routed
- **THEN** the alert "Failed to load deeplink. Will navigate back to home screen" is shown and the user is returned to the Home tab

#### Scenario: The interstitial does not fetch the entity
- **WHEN** the interstitial resolves a link
- **THEN** no Firestore read is issued for the linked entity by the interstitial

### Requirement: The resolver encodes the routability matrix in one place
`feature/shell` SHALL declare a `DeepLinkResolver` that maps a `DeepLink` to a routable target or to "not routable":

| `DeepLink` case | Target | Routable on Android? |
|---|---|---|
| `NewLoop` | `LoopDetail(loopId)` | yes |
| `NewLoopComment` | `LoopDetail(loopId)` | yes |
| `VideoCall` | `KornerDetail(kornerId)` | yes |
| `NewEvent` | `KornerDetail(kornerId)` | yes — the community's detail |
| `NewArticle` | `KornerDetail(kornerId)` | yes — the community's detail |
| `NewForum` | `KornerDetail(kornerId)` | yes — the community's detail |
| `NewComment` | `KornerDetail(kornerId)` | yes — the owning community's detail |

**The four community-scoped links resolve to the Korner detail.** Event Detail, Post Detail and Forum Detail are excluded from the Android port (`docs/screen-inventory.md:229-237` records the stakeholder decision), so there is no dedicated screen to open for them. The community that owns the content is still live in the port and its detail screen is where the content is listed, so a tap lands the user on the community the notification is about rather than failing with a "not routable" alert. iOS opens the dedicated detail screen for these; Android opens the community.

`NewComment` carries only a forum id, with no community id — the forum's community is not known to the client, so the link SHALL resolve through the forum's own document.

A `VideoCall` link SHALL resolve to the **Korner detail**, not to the video-call screen — iOS's `handleCallLink` pushes a `KornerDetailController` (`DeeplinkViewController.swift:135-155`), and the call itself stays reachable only from the Korner detail.

A `KornerDetail` target SHALL carry the Korner's **document id**, because the Korner detail route is addressed by document id. The resolver SHALL translate the link's `communityId` field into the document id with a single `Communities.whereEqualTo("communityId", id)` lookup before navigating. A community id that does not resolve SHALL be treated as not routable.

#### Scenario: A loop link resolves to the loop detail
- **WHEN** a `DeepLink.NewLoop("community-1", "loop-1")` is resolved
- **THEN** the resolver returns `LoopDetail("loop-1")`

#### Scenario: A video-call link resolves to the Korner detail
- **WHEN** a `DeepLink.VideoCall("community-1")` is resolved and `community-1` maps to the document id `doc-1`
- **THEN** the resolver returns `KornerDetail("doc-1")`

#### Scenario: An article link resolves to the owning community's detail
- **WHEN** a `DeepLink.NewArticle("article-1", "community-1")` is resolved and `community-1` maps to the document id `doc-1`
- **THEN** the resolver returns `KornerDetail("doc-1")`

#### Scenario: A forum comment link resolves through the forum's community
- **WHEN** a `DeepLink.NewComment("forum-1")` is resolved and the forum `forum-1` belongs to a community that maps to the document id `doc-1`
- **THEN** the resolver returns `KornerDetail("doc-1")`

#### Scenario: An unresolvable community is not routable
- **WHEN** a `DeepLink.VideoCall("missing")` is resolved and no `Communities` document carries `communityId = "missing"`
- **THEN** the resolver reports it as not routable

### Requirement: MainActivity delivers cold and warm deep links
`MainActivity` SHALL hand every deep link it receives to the `DeepLinkManager`:

- A **cold** deep link — the activity's `onCreate` — SHALL be handed over as soon as the intent is read.
- A **warm** deep link — `onNewIntent` — SHALL be handed over at that moment.

The deep link SHALL be resolved from the intent's `data` URI or from the intent's `extras`, whichever carries one — see the requirement "A backgrounded or killed push notification carries its deep link".

`MainActivity` SHALL call `setIntent(intent)` inside `onNewIntent`, so the current intent stays accurate for a later configuration change or process restore.

The `DeepLinkManager` SHALL be reached through a Hilt `@EntryPoint` on the `SingletonComponent`, following the P8 `PremiumGateEntryPoint` precedent.

#### Scenario: A cold deep link is handed over
- **WHEN** the activity is created with an intent whose data is `kornerz://loop/loop-1`
- **THEN** the `DeepLinkManager` receives a loop deep link carrying `loopId = "loop-1"`

#### Scenario: A warm deep link is handed over
- **WHEN** the activity receives `onNewIntent` with data `kornerz://forum/forum-1`
- **THEN** the `DeepLinkManager` receives a forum deep link carrying `forumId = "forum-1"`

#### Scenario: The warm intent replaces the current intent
- **WHEN** `onNewIntent` runs
- **THEN** `getIntent()` afterwards returns the new intent

#### Scenario: A launch with no deep link hands over nothing
- **WHEN** the activity is created with no intent data and no push payload in its extras
- **THEN** the `DeepLinkManager` receives nothing

### Requirement: A tapped push notification carries its deep link
`KornerzFcmService` SHALL build the tray notification's `PendingIntent` with the deep link derived from the **push payload** — the dialect iOS reads in `DeepLinksManager.handleDeepLink(info:)` (`:85-134`):

| `pushType` | payload ids | resulting link |
|---|---|---|
| `NewEvent` | `communityId`, `eventId` | `kornerz://community/{communityId}/event/{eventId}` |
| `NewArticle` | `communityId`, `articleId` | `kornerz://community/{communityId}/article/{articleId}` |
| `NewForum` | `communityId`, `forumId` | `kornerz://community/{communityId}/forum/{forumId}` |
| `VideoCall` | `communityId` | `kornerz://community/{communityId}/call` |
| `NewComment` | `forumId` | `kornerz://forum/{forumId}` |
| `NewLoop` | `communityId`, `loopId` | `kornerz://loop/{loopId}` |
| `LoopComment` | `loopId` | `kornerz://loop/{loopId}` |

The `PendingIntent` SHALL carry that URI as its intent data, so tapping the notification opens the app **and** routes. The `PendingIntent` SHALL NOT be built from `getLaunchIntentForPackage` any more.

A payload whose `pushType` is missing or unrecognised SHALL fall back to the plain launch intent, so the notification still opens the app.

The push dialect SHALL stay distinct from the in-app notification-document dialect: the push payload carries `pushType` / `communityId` / `eventId` / `articleId` / `forumId` / `loopId`, while the Firestore notification document carries `notificationType` / `notificationReferenceId` / `notificationCommunityId`.

#### Scenario: A push tap carries the deep link
- **WHEN** FCM delivers a data payload with `pushType = "VideoCall"` and `communityId = "community-1"`
- **THEN** the tray notification's `PendingIntent` carries `kornerz://community/community-1/call` as its intent data

#### Scenario: A loop push tap carries the loop URI
- **WHEN** FCM delivers a data payload with `pushType = "NewLoop"` and `loopId = "loop-1"`
- **THEN** the tray notification's `PendingIntent` carries `kornerz://loop/loop-1`

#### Scenario: An unknown push type falls back to the launch intent
- **WHEN** FCM delivers a data payload with no `pushType`
- **THEN** the tray notification's `PendingIntent` opens the app without a deep link, and nothing crashes

#### Scenario: The notification still displays
- **WHEN** FCM delivers a notification payload
- **THEN** the tray notification is still shown on the `kornerz_notifications` channel

### Requirement: A backgrounded or killed push notification carries its deep link
The tray notification's `PendingIntent` is only one of the two ways a push notification reaches the app. The Firebase SDK displays a notification **itself** whenever the app is backgrounded or has been killed, and in that case `onMessageReceived` is never called — the app never gets to build its own `PendingIntent`. The SDK then launches the app from the payload's `click_action`, with the message's `data` payload copied into the launch intent's extras.

Without handling that second path, a notification that arrives while the app is not running opens the app but lands the user on the Home tab instead of the screen the notification is about. iOS has no such split: `UNUserNotificationCenterDelegate.didReceive` fires for a tap in every app state, and the payload is in `userInfo` either way.

`DeepLinkPayloadParser` SHALL declare a `CLICK_ACTION` constant whose value is `kornerz://deeplink`, and `app/src/main/AndroidManifest.xml` SHALL declare an `<intent-filter>` on `MainActivity` for that action with the `DEFAULT` category, so the SDK's launch intent resolves to the app.

`MainActivity` SHALL resolve a deep link from **either** transport:

- the intent's `data` URI, when it is a `kornerz://` URI that matches the link grammar; and
- the intent's `extras`, when they carry a push payload.

The extras transport SHALL read the payload keys `pushType`, `communityId`, `eventId`, `articleId`, `forumId`, `loopId`, `notificationType`, `notificationReferenceId`, and `notificationCommunityId`, and SHALL ignore the Firebase SDK's own `google.*` bookkeeping extras.

The extras transport SHALL accept **both** payload dialects. The push dialect (`pushType` / `communityId` / `eventId` / …) is tried first; the notification-record dialect (`notificationType` / `notificationReferenceId` / `notificationCommunityId`) is the fallback. The backend describes a notification once for the in-app list and once for the deep link, and which of the two it puts on the wire is not something this repo can verify — so a payload carrying either dialect is routable.

The `PendingIntent` the app builds itself SHALL carry the payload in **both** transports — the `kornerz://` URI as its intent data and the payload as its extras — so the two paths cannot drift apart. They are built from the same payload, by the same parser, in the same place.

#### Scenario: The click action is declared
- **WHEN** the merged manifest of the `devDebug` variant is inspected
- **THEN** `MainActivity` declares an intent filter whose action is `kornerz://deeplink`

#### Scenario: A backgrounded push tap carries the deep link
- **WHEN** the app is launched by the Firebase SDK with an intent whose extras carry `pushType = "VideoCall"` and `communityId = "community-1"`
- **THEN** the `DeepLinkManager` receives a `VideoCall` deep link carrying `communityId = "community-1"`

#### Scenario: A record-dialect payload is routable
- **WHEN** the app is launched with an intent whose extras carry `notificationType = "NewEvent"`, `notificationReferenceId = "event-1"` and `notificationCommunityId = "community-1"`
- **THEN** the `DeepLinkManager` receives a `NewEvent` deep link carrying `eventId = "event-1"` and `communityId = "community-1"`

#### Scenario: The extras transport ignores the SDK's own keys
- **WHEN** the app is launched with extras carrying `google.message_id` and no `pushType`
- **THEN** the `DeepLinkManager` receives nothing

#### Scenario: The two transports agree
- **WHEN** the app's own `PendingIntent` for a `VideoCall` push is tapped
- **THEN** the `DeepLinkManager` receives the same `VideoCall` deep link the extras transport would produce from the same payload

### Requirement: A video-call push refreshes the Korner detail that is already open
iOS treats a **video-call** push specially: when the Korner the call belongs to is already the top screen of the selected tab, the open screen **re-reads its data** instead of a second copy of the Korner detail being pushed on top of it — `DeepLinksManager.handleDeepLink(info:)` calls `checkIfKornerDetailAlreadyOpened` and, when it matches, posts the `.refreshKornerDetail` notification (`kornerz/Kornerz/DeepLinks/DeeplinkManager.swift:108-116, 168-183`).

Android SHALL reproduce that behaviour:

- `DeepLinkTarget.KornerDetail` SHALL carry an `isVideoCall` flag, set by the resolver for a `VideoCall` link and clear for every other link.
- The shell SHALL determine that the Korner detail is "already open" only when the **active** tab is the Kornerz tab and the Korner detail it shows is the destination the link names. A Korner left behind in an inactive tab does not count — Navigation-Compose keeps an inactive tab's destination composed, while iOS reads the *selected* tab's stack.
- When the link is a video-call link and the Korner is already open, the shell SHALL request a refresh of that screen instead of navigating.
- `feature/kornerz` SHALL declare a `KornerDetailRefreshBus` carrying the korner id, and `KornerDetailViewModel` SHALL re-read the Korner when a refresh is requested for the korner it is showing. The refresh SHALL keep the current content on screen while the read is in flight, the same as the pull-to-refresh path.

A video-call push for a Korner that is **not** already open SHALL navigate to the Korner detail, exactly as before.

#### Scenario: An open Korner is refreshed, not duplicated
- **WHEN** a `VideoCall` push for a community that maps to the Korner document id `doc-1` arrives while the Kornerz tab is showing the Korner detail for `doc-1`
- **THEN** the open Korner detail re-reads its data and no second Korner detail is pushed

#### Scenario: A closed Korner is opened
- **WHEN** a `VideoCall` push for a community that maps to the Korner document id `doc-1` arrives while no Korner detail is open
- **THEN** the shell navigates to the Korner detail for `doc-1`

#### Scenario: An inactive tab does not count as open
- **WHEN** a `VideoCall` push for the Korner document id `doc-1` arrives while the Kornerz tab is not the active tab
- **THEN** the shell navigates to the Korner detail for `doc-1`

#### Scenario: A non-video-call push opens the Korner
- **WHEN** a `NewEvent` push for the Korner document id `doc-1` arrives while the Korner detail for `doc-1` is open
- **THEN** the shell navigates to the Korner detail for `doc-1` rather than refreshing in place

### Requirement: The deep link is delivered before the premium gate
The shell SHALL release a pending deep link before the premium gate opens. The deep-link destination SHALL therefore be on the back stack first, and the paywall SHALL be pushed on top of it.

This mirrors iOS, where `AppTabController.viewDidLoad` calls `DeepLinksManager.shared.notifyAppLaunch()` (`SwiftUI/TabView/AppTabController.swift:24`) after registering the entitlement sink, so the deep link is pushed and the paywall is presented modally over it.

#### Scenario: The link lands before the paywall
- **WHEN** a free user with a pending deep link opens the shell
- **THEN** the deep-link destination is pushed first and the paywall is pushed on top of it

#### Scenario: Completing the purchase reaches the destination
- **WHEN** the user completes a purchase and the paywall unwinds
- **THEN** the user lands on the deep-link destination

### Requirement: The community lookup resolves by the communityId field
`CommunitiesRepository.fetchById(id)` SHALL look the community up with `whereEqualTo("communityId", id)` on the `Communities` collection, matching iOS `CommunitiesRepository.fetchCommunity(with:)` (`kornerz/Kornerz/Backend/Repositories/CommunitiesRepository.swift:241`) and `FireStoreServiceManager.getCommunityDetailsFor` (`kornerz/Kornerz/FirestoreService/APICommunity.swift:475`).

It SHALL NOT look the community up by the Firestore document id. The `communityId` field and the document id are different values on the same document — iOS resolves by the field and then overwrites the model's id with the document id (`APICommunity.swift:483`) — so a document-id lookup misses.

`core/model`'s `Community` SHALL gain a `communityId` property mapped to the `communityId` field, so the two identifiers are separately nameable. The property SHALL be nullable with a `null` default, so no existing construction site changes.

#### Scenario: A community is found by its communityId field
- **WHEN** `fetchById("community-1")` is called and the `Communities` collection holds a document whose `communityId` field is `community-1`
- **THEN** the community is returned and its `communityId` is `community-1`

#### Scenario: A document-id lookup is not used
- **WHEN** `fetchById` is inspected
- **THEN** it issues a `whereEqualTo("communityId", …)` query and not a `document(…)` lookup

#### Scenario: A missing community returns null
- **WHEN** no `Communities` document carries `communityId = "missing"`
- **THEN** `fetchById("missing")` returns `null`


## References
- [`docs/architecture.md`](../../../docs/architecture.md) — MVVM + Repository + unidirectional data flow; navigation through `Channel<NavEvent>`; module boundaries.
- [`docs/screen-inventory.md`](../../../docs/screen-inventory.md) §"What is NOT in the Android port" — the excluded Event Detail, Post Detail and Forum Detail screens that the community-scoped links resolve to the Korner detail instead.
- `kornerz/Kornerz/DeepLinks/DeeplinkManager.swift` — the iOS deep-link model, the push-payload parser and the video-call refresh-in-place path.
- `kornerz/Kornerz/DeepLinks/DeeplinkViewController.swift` — the iOS interstitial that fetches the linked entity and pushes the destination.
- `kornerz/Kornerz/App/AppDelegate.swift` — the iOS notification-tap delegate.
- `openspec/specs/notifications-push-display/spec.md` — the FCM `click_action` contract and the tray notification's `PendingIntent`.
