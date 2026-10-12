## Context

The Android app is mid-phase rollout. P0–P4 ship a populated Home, Kornerz, and Nooks tab on top of the shared Firebase project `kornerz-72fba` and the iOS-mirrored domain models. The Nooks tab's `LiveKornerCard` already renders a static "Join" button with a code comment that defers the navigation to the P9 video calling phase. The `Nook` model already carries `sessionId: String?` (populated by `NookFirestoreApi` from the Firestore `sessionId` field), so the entry point and the Vonage session identifier are both in place. The iOS app's `VideoCallView` / `VideoCallViewModel` / `OpenTokTokenService` / `WebSocketService` are the contract sources.

The team has decided to ship P9 ahead of P5 (Loops), P6 (Profile/Notifications), P7 (Create Community/Admin), and P8 (Premium/IAP). Those phases are independent siblings in the dependency graph and have no calls into the video call path. Skipping them is acceptable. The Android team needs an end-to-end "two phones join a community room" demo, and the iOS Vonage API key `47628671` is reused for the demo and will be rotated to a dedicated Android key before shipping.

The architecture (MVVM + Repository + UDF) is fixed by `docs/architecture.md`. The Vonage Android SDK 2.34.0 is the canonical implementation choice per `docs/project-brief.md` decision 2 ("Vonage (OpenTok) — same provider as iOS"). Compose is the UI framework; Vonage's `Publisher.view` and `Subscriber.view` are Android `View`s, so the screen is built as a thin Compose layer with two `AndroidView` wrappers per `docs/project-brief.md` decision 1 ("Compose-first, with `AndroidView` wrappers only where vendored SDKs force it").

## Goals / Non-Goals

**Goals:**

- Wire the static Join button on the Nooks tab's `LiveKornerCard` to a real navigation that opens a video call screen.
- Open a Vonage video room over the same WebSocket token service the iOS app uses (`ws://ws.kornerz.com:80/`).
- Mirror the iOS `VideoCallView` UX: top toolbar (community name + small publisher tile), subscriber grid area, bottom toolbar (end, flip, mute, video, hand-raise), and a centered loading overlay.
- Land the call within the existing Nooks-tab nested `NavHost` so the back stack is correct (system back from the call returns to the Nooks list, Nooks tab remains selected, the bottom bar is hidden while the call is on top).
- Run on the shared Firebase project `kornerz-72fba` with zero new backend work; reuse the iOS Vonage API key for the demo.
- Request `CAMERA` and `RECORD_AUDIO` at runtime before connect; fail gracefully on denial.

**Non-Goals:**

- iOS raise-hand signal mechanism (D4).
- Dynamic "9 spots left" count from `getusercountsinasession` (D5).
- Foreground service for ongoing calls (D6, will revisit if it becomes trivial).
- Complex subscriber grid math — linear/fixed layout this phase (D7).
- Premium gating (D3) — demo only.
- Entry from the Kornerz tab — single entry point this phase, Nooks tab only.
- Deep-link entry into the video call.
- Screen recording (admin-only iOS feature, deferred).
- Reconnection on transient network drops.
- Rotation of the Vonage API key to a dedicated Android key (will happen before shipping, not in this change).

## Decisions

### D1. Single new module `feature/videocall`, imported only by `app`

Mirrors the convention used by `feature/nooks`, `feature/kornerz`, etc. The module owns its `VideoCallScreen` / `VideoCallContent` / `VideoCallViewModel` / `VideoCallLoadingState` / `PublisherContainer` / `SubscriberContainer` and its data layer (`VideoCallRepository`, `SessionTokenService`, `VonageSessionController`). The module's `build.gradle.kts` applies the same plugin alias set as `feature/nooks` (`android.library`, `kotlin.android`, `kotlin.kapt`, `kotlin.compose`, `kotlin.serialization`, `hilt`).

**Why:** keeps the video-call surface as a discrete, arch-enforced unit (arch §2.5 — `feature/*` is imported by `app` ONLY). Mirrors the iOS module split (`SwiftUI/VideoCall/`, `Backend/Services/OpenTok*`, `Backend/Services/WebSocket*`).

**Alternatives considered:**
- Put the data layer in `core/network` and only the screen in `feature/videocall`. Rejected: the WebSocket handshake and the Vonage SDK are video-call-specific; `core/network` is for cross-cutting clients (Firestore, Cloud Functions, OkHttp, Play Billing). Keeping the Vonage controller inside `feature/videocall` localizes the SDK and makes ProGuard rules easier to scope.
- Bundle the screen into `feature/nooks` (the entry point is the Nooks tab). Rejected: video call is a real product surface, not a nooks sub-feature, and bundling would couple `feature/nooks` to the Vonage SDK.

### D2. Session token via `OkHttp` `WebSocket`, mirroring iOS `WebSocketService.swift`

The iOS app connects to `ws://ws.kornerz.com:80/`, waits for the `connected` state, sends the Vonage `sessionId` as bytes, awaits the JSON `{"msg": "<token>"}` reply, then disconnects. We re-implement the same one-shot flow using OkHttp's `WebSocket` (arch §3 lists `OkHttp | WebSocket for Vonage (P9)`). No auto-reconnect, no 30 s ping, no 5 s reconnect delay this phase — iOS's reconnect logic protects against flaky networks, but the demo's Wi-Fi/emulator network is stable and a simpler one-shot connection is easier to verify.

**Why:** byte-for-byte parity with iOS over the same `ws://ws.kornerz.com:80/` endpoint, so the token-issuing service needs no changes.

**Alternatives considered:**
- Use a `Ktor` WebSocket client. Rejected: OkHttp is already a transitive dependency via Firebase; no new third-party lib needed (arch §3).
- Implement the full iOS reconnect/ping logic. Deferred — the demo doesn't need it; reconnection can be a follow-up if it surfaces in testing.

### D3. Vonage session controller wraps `Publisher` and `Subscriber` via `AndroidView`

The Vonage Android SDK exposes `Publisher.view` and `Subscriber.view` as Android `View`s (per `docs/project-brief.md` decision 1). `PublisherContainer` and `SubscriberContainer` are `AndroidView { ... }` wrappers that own a `Publisher` / `Subscriber` instance for the lifetime of the composable and update the `View` reference when the stream changes. This mirrors the iOS `UIViewRepresentable` wrappers in `SwiftUI/VideoCall/PublisherContainer.swift` and `SubscriberContainer.swift`.

**Why:** Compose cannot host the SDK's `View`s directly. `AndroidView` is the canonical Compose ↔ View interop mechanism and is already documented in arch §1.

**Alternatives considered:**
- Use `ComposeView` inside the `Publisher` / `Subscriber`. Rejected: the SDK does not support embedding its rendering pipeline in a `ComposeView`.
- Wrap the entire video call screen in a single `AndroidView`. Rejected: Compose owns the toolbar, the loading state, and the layout; mixing Compose + View at the screen level defeats the Compose-first principle.

### D4. Linear/fixed subscriber list this phase (iOS grid math deferred)

iOS `VideoCallViewModel.subscriberGridRows()` produces a 2-column grid with specific row-count logic. This phase renders a `LazyColumn` of `SubscriberContainer`s with a fixed 2-column `Row`-and-`Spacer` layout — good enough for a 1- to 4-participant demo, never breaks the layout for a 5+ participant room, and avoids hand-rolling the iOS row-count math.

**Why:** the demo audience is 1–4 participants. The grid math is a polish item, not a correctness item.

**Alternatives considered:**
- Port the iOS `subscriberGridRows` + `subscriberGridSize` exactly. Deferred: matches iOS pixel-for-pixel but adds risk for a non-critical path.
- Use a 3rd-party layout (e.g., `FlowRow`). Rejected: introduces a new dep outside arch §3.

### D5. Permissions via `rememberLauncherForActivityResult`, declared in manifest

`CAMERA` and `RECORD_AUDIO` are requested at runtime from `VideoCallScreen` before invoking `VideoCallRepository.connect(sessionId)`. Denial renders an inline error card with a "Grant permissions" button that re-launches the request. `BLUETOOTH_CONNECT` and `MODIFY_AUDIO_SETTINGS` are declared in `AndroidManifest.xml` (Vonage requires them) but are **not** runtime-requested this phase — Android 12+ requires `BLUETOOTH_CONNECT` at runtime, but the demo runs on emulators and phones with built-in mics, and the SDK's documented Bluetooth path is not exercised by the call.

**Why:** the demo only needs camera + mic. Skipping the Bluetooth runtime request keeps the permission flow to two prompts (Android handles it well).

**Alternatives considered:**
- Use the `accompanist-permissions` library. Rejected: arch §3 has no such dependency; the Compose `rememberLauncherForActivityResult` API is sufficient.

### D6. Single `VideoCallViewModel` per call, keyed by `sessionId`

`VideoCallViewModel` is `@HiltViewModel`-managed by `hiltViewModel()` inside `composable("videocall/{sessionId}")`. The `sessionId` is read from `SavedStateHandle["sessionId"]` (same pattern as `NookDetailViewModel` from P4). The `VideoCallRepository` is a `@Singleton` and is injected into the VM.

**Why:** matches arch §2.6 ("ViewModel (Hilt-managed) — per-screen state via `hiltViewModel()`") and the per-screen instance pattern used by every other VM in the app.

### D7. `LiveKornerCard` Join button is its own tappable region, separate from the card's row click

Currently the entire `LiveKornerCard` row is a single `Modifier.clickable { onClick(korner.id) }`. The Join button is a non-interactive visual. This change makes the Join button its own `clickable { ... }` region that emits the new `NooksNavEvent.JoinVideoCall(sessionId)`. The row-level click is preserved (still routes to Korner detail).

**Why:** the iOS `NookView` keeps the join action separate from the card tap. We match that pattern so the rest of the card stays usable.

### D8. Bottom navigation bar is hidden while the video call is on top

`feature/shell`'s Nooks tab nested `NavHost` adds `videocall/{sessionId}` as a new destination. While that destination is in the back-stack, the bottom navigation bar visibility is `false` (mirrors iOS, which pushes the call full-screen). On system back, the bar reappears. The originating tab (Nooks) is preserved.

**Why:** the call is a focused, full-screen experience. A visible bottom bar would steal pixels and look out of place.

## Risks / Trade-offs

- **Vonage SDK API surface drift** — the iOS code uses `OTXCFramework 2.33.0`; we're pinning Android SDK `2.34.0`. The Kotlin API names (`Session.Builder`, `Publisher.Builder`, `Subscriber.Builder`, `Session.SessionListener`, `PublisherKit.PublisherListener`, `SubscriberKit.SubscriberListener`) follow the same names as iOS but are not 1:1 identical. → Mitigation: cap the public surface of `VonageSessionController` to a small, project-specific interface (`connect`, `disconnect`, `publishers`, `subscribers`); keep all SDK call sites in one file.

- **WebSocket `ws://` (not `wss://`)** — the iOS code connects to a cleartext `ws://` endpoint. Android 9+ blocks cleartext traffic by default. → Mitigation: add a `network_security_config.xml` allowing cleartext to `ws.kornerz.com` for the dev flavor only; the prod flavor is unchanged. Documented in tasks.

- **Foreground service deferred** — when the user backgrounds the app mid-call, the call is silently torn down by the OS. → Mitigation: documented in the proposal as out of scope; revisit after the demo if it surfaces as a real issue.

- **Permission denial UX** — a first-time `RECORD_AUDIO` denial on Android 13+ shows a one-time "Don't ask again" checkbox. After that, the user must grant via system settings. → Mitigation: the "Grant permissions" button on the denial card deep-links to the system app-info screen via `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))`.

- **API key reuse** — using the iOS Vonage API key for the demo means the same backend billing counter ticks for both apps. → Mitigation: a `TODO(android-key-rotation)` marker in `VonageConfig.kt` and a tasks.md checklist item that blocks ship.

- **`LiveKornerCard` re-layout risk** — adding a clickable region to the existing row may shift the row's vertical alignment if not isolated. → Mitigation: the Join button gets its own `Modifier.clickable` and is wrapped in a `Box` with the same dimensions as today; the row layout is unchanged.

## Migration Plan

- This change adds new code and modifies one composable in `feature/nooks` (`LiveKornerCard`). No data migrations, no schema changes, no backend deploys.
- Rollout: the Vonage `apiKey` is shared with iOS for the demo. Before any public release, a follow-up change rotates the Android app to a dedicated Vonage project API key.
- Rollback: revert the commit. No data state is left behind in Firestore or in the Vonage project (sessions are ephemeral).

## Open Questions

- Whether the Kornerz tab's nook tile (rendered inside `feature/kornerz`'s `CurrentNookSection` and `NooksListSection`) should also wire a Join button in a follow-up change. Out of scope this phase; the entry point is Nooks tab only.
- Whether the foreground service is required for the demo's intended runtime. Will be answered empirically during the demo rehearsal.
- Whether the Android team needs a separate Vonage project (recommended) or whether the shared iOS project is sufficient. Tracked as `TODO(android-key-rotation)`.
