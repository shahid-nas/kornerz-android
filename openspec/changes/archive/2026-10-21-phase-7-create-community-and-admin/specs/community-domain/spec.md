# community-domain Specification

## Purpose
TBD - created by archiving change phase-7-create-community-and-admin. Update Purpose after archive.

## ADDED Requirements

### Requirement: Community data class mirrors iOS Models/Community.swift
The `Community` `@Serializable` data class in `core/model/Community.kt` SHALL mirror iOS `Models/Community.swift` field-for-field, with `@SerialName` mapping the iOS Firestore field names:

| Kotlin field | Firestore field (`@SerialName`) | Type |
|---|---|---|
| `id` | `"id"` | `String` |
| `code` | `"idCommunity"` | `String` |
| `name` | `"name"` | `String` |
| `description` | `"description"` | `String?` |
| `themeIdRaw` | `"themeId"` | `Int` (0 = DEFAULT, 1 = RED, 2 = BLUE, 3 = YELLOW, 4 = PURPLE, 5 = GREEN) |
| `imageURL` | `"image"` | `String?` (legacy IPFS) |
| `thumbnailURL` | `"thumbnail"` | `String?` (legacy IPFS) |
| `firebaseImageURL` | `"firebaseImageUrl"` | `String?` |
| `firebaseThumbnailURL` | `"firebaseThumbnailUrl"` | `String?` |
| `isCreator` | `"isCreator"` | `Boolean` |
| `createdByUserId` | `"userId"` | `String` |
| `createdAtTimestamp` | `"createdDate"` | `Timestamp?` (Firestore `Timestamp` via `kotlinx-datetime` Instant mapping) |

A derived `theme: CommunityTheme` property SHALL map `themeIdRaw` to the `CommunityTheme` enum. A derived `userIsAdmin(currentUserCode: String): Boolean` property SHALL return `createdByUserId == currentUserCode`. A derived `coverImageURL: String?` property SHALL return `firebaseImageURL ?: imageURL` (the v4-first / IPFS-fallback chain mirrors iOS `Models/Community.swift` lines 47–58).

#### Scenario: Read a community document from Firestore
- **WHEN** the Firestore API returns a `Communities/{code}` document with the iOS-shaped fields
- **THEN** the decoded `Community` instance has all fields populated and `theme` resolves to the matching `CommunityTheme` enum value

#### Scenario: User is admin check
- **WHEN** `currentUserCode == createdByUserId`
- **THEN** `community.userIsAdmin(currentUserCode) == true`

#### Scenario: Cover image URL fallback chain
- **WHEN** `firebaseImageURL == "https://storage.googleapis.com/.../cover.jpg"` and `imageURL == null`
- **THEN** `coverImageURL == "https://storage.googleapis.com/.../cover.jpg"`

### Requirement: CommunityTheme enum has six values matching iOS
The `CommunityTheme` enum SHALL have exactly six values, ordered and numbered to match iOS `CommunityThemeId`:

```kotlin
enum class CommunityTheme(val rawValue: Int, val displayName: String) {
    DEFAULT(0, "Default"),
    RED(1, "Red"),
    BLUE(2, "Blue"),
    YELLOW(3, "Yellow"),
    PURPLE(4, "Purple"),
    GREEN(5, "Green");
    
    companion object {
        fun fromRawValue(raw: Int): CommunityTheme = entries.firstOrNull { it.rawValue == raw } ?: DEFAULT
    }
}
```

Each value SHALL expose a `gradientBrush: List<Color>` (3 colors per theme) matching iOS `SelectCommunityThemeVC.SelectionView` colors:

| Theme | Color 1 | Color 2 | Color 3 |
|---|---|---|---|
| DEFAULT | `#F2F4F6` | `#E9EAEE` | `#D1D3D8` |
| RED | `#FF6B6B` | `#EE5A52` | `#C53D3D` |
| BLUE | `#3B82F6` | `#2563EB` | `#1E40AF` |
| YELLOW | `#FBBF24` | `#F59E0B` | `#D97706` |
| PURPLE | `#A855F7` | `#9333EA` | `#7E22CE` |
| GREEN | `#22C55E` | `#16A34A` | `#15803D` |

Mirrors iOS `SelectCommunityThemeVC.swift` lines 32–68.

#### Scenario: Map iOS themeId 3 to YELLOW
- **WHEN** a Firestore document has `themeId == 3`
- **THEN** `Community.theme` resolves to `CommunityTheme.YELLOW`

#### Scenario: Unknown themeId falls back to DEFAULT
- **WHEN** a Firestore document has `themeId == 99` (legacy or corrupted value)
- **THEN** `Community.theme` resolves to `CommunityTheme.DEFAULT`

### Requirement: CommunityAdmin projection bundles community with its counts
The `CommunityAdmin` data class in `core/model/CommunityAdmin.kt` SHALL bundle a `community: Community` with `memberCount: Int` and `nookCount: Int`. Used by `CommunitiesRepository.fetchOwnedByUser(userCode): Result<List<CommunityAdmin>>` as the canonical projection of an admin-owned community with its live counts. A `CommunityAdmin.from(community: Community, memberCount: Int, nookCount: Int)` factory is provided.

#### Scenario: Compose CommunityAdmin from a Community + counts
- **WHEN** `CommunitiesRepository.fetchOwnedByUser()` completes with three communities
- **THEN** it returns `List<CommunityAdmin>` where each entry has the matching `community.code`, the live `memberCount`, and the live `nookCount`

### Requirement: CommunityThemeCard projection for the theme picker
The `CommunityThemeCard` data class in `core/model/CommunityThemeCard.kt` SHALL bundle a `theme: CommunityTheme` with its `displayLabel: String` (the localized name, e.g., "Red", "Blue") and `gradientBrush: List<Color>`. Used by `SelectCommunityThemeViewModel` to render the 2×3 grid of theme cards on step 9.4 without each card needing to re-derive its colors.

#### Scenario: Render theme card for RED
- **WHEN** `SelectCommunityThemeViewModel` builds the grid
- **THEN** it emits six `CommunityThemeCard`s, one per enum value, each with the matching `gradientBrush`

## References
- `kornerz/Kornerz/Models/Community.swift` — iOS source-of-truth for the Community schema
- `kornerz/Kornerz/HomeVC/CreateCommunity/SelectCommunityThemeVC/SelectCommunityThemeVC.swift` — iOS source-of-truth for the six theme colors and gradient definitions
- `kornerz/Kornerz/Backend/Repositories/CommunitiesRepository.swift` — iOS source-of-truth for `CommunityAdmin` projections
- `docs/phases.md` — Phase 7 model deliverables
