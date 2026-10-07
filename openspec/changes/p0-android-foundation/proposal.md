## Why

The Kornerz iOS app exists today with no Android counterpart. Before any feature work can land, we need a working Android project skeleton that the entire team can build on — one that compiles, launches into a branded splash → 5-tab shell, and uses the same design language (colors, Satoshi font, theme) as iOS so feature work in P1+ matches visually out of the gate.

## What Changes

- **Create a new Android project** (`KornerzAndroid/`) with module structure mirroring `docs/project-brief.md` §4.2 (`app/` + `core/{model, network, storage, ui}` + `feature/{splash, shell}` placeholders for future phases).
- **Adopt Compose-first** with Material 3, Hilt for DI, Navigation-Compose, Coil for images, ExoPlayer for future video playback.
- **Add design tokens** in `core/ui/` mapped 1:1 from iOS `Config.swift` (`AppColor.C5D47ED → #5D47ED`, Satoshi font family, 8pt spacing grid, typography scale).
- **Add a 5-tab bottom-navigation shell** (Home, Kornerz, Nooks, Loops, Klouders) with empty-state placeholders. Mirrors iOS `AppTabController.swift`.
- **Add a branded splash screen** that fades into the shell.
- **Wire Firebase** to the shared project (`kornerz-72fba`) — init + anonymous user setup; no auth yet (handled in P1).
- **Add build flavors**: `dev` (matches iOS `KornerzDev` flavor) and `prod` (matches `Kornerz`).
- **Add CI** that runs `./gradlew :app:assembleDevDebug` on every PR.
- **Set `minSdk = 26`, `targetSdk = latest stable`** per `docs/project-brief.md` §11.6.

## Capabilities

### New Capabilities
- `android-app-shell`: `MainActivity`, `NavigationCompose` host, 5-tab `NavHost` (Home/Kornerz/Nooks/Loops/Klouders), splash → shell transition, theming plumbing (Material 3 `KornerzTheme`).
- `design-system`: Kornerz color palette (mapped from iOS `AppColor`), Satoshi font family (bundled `.otf` from `kornerz/Kornerz/Fonts/`), typography scale, shape, 8pt spacing tokens. Consumed by every screen in P1+.
- `android-build`: Multi-module Gradle setup (KTS, version catalog), `dev`/`prod` flavors, Hilt + Compose + Navigation + Coil dependencies, CI workflow running `assembleDevDebug`.

### Modified Capabilities
- _None — `openspec/specs/` is empty; this change introduces the first capabilities._

## Impact

- **New code**: ~30–40 Kotlin files (mostly empty Compose scaffolds + design tokens). No business logic yet.
- **New external dependencies**: Compose BOM, Hilt, Navigation-Compose, Coil, Firebase Android SDK (BoM), Firebase Firestore Android SDK (for P0 onboarding only).
- **External systems**: Firebase project `kornerz-72fba` — Android team needs Editor role to upload the `google-services.json`.
- **No impact on iOS.** This change is additive and lives entirely in `KornerzAndroid/`.
- **No breaking changes.** The iOS app and `docs/project-brief.md`, `docs/screen-inventory.md`, `docs/phases.md` are unchanged.

## References

- [Architecture, Patterns & Conventions](../../../docs/architecture.md) — canonical patterns every implementation must follow (UDF, `AsyncValue<T>`, `Channel<NavEvent>`, Hilt scopes, library catalog, naming conventions, anti-patterns).
- [Project Brief](../../../docs/project-brief.md) — locked decisions (Compose-first, Vonage, shared Firebase, full parity, separate codebase, minSdk 26, English only).
- [Phased Delivery Plan](../../../docs/phases.md) — phase boundaries; this is P0.
- [Screen Inventory](../../../docs/screen-inventory.md) — 77-screen live catalog (no screens ship in P0; placeholders only).