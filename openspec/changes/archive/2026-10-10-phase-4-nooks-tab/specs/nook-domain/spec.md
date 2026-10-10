# nook-domain Specification

## Purpose
The nook domain defines the `NookDetail` aggregate in `core/model` that pairs a `Nook` (from `korner-domain`, P3) with its parent `Korner` (from `korner-domain`, P3). The aggregate is consumed by `feature/nooks`'s `NookDetailViewModel` and persisted across navigation by `SavedStateHandle["nookId"]`. No changes to `Nook` or `Korner` are introduced; this capability only adds the composite projection.

## References
- [`docs/architecture.md`](../../../docs/architecture.md) — `core/model` layout, `@Serializable` data classes, single source of truth for cross-module data.
- [`docs/phases.md`](../../../docs/phases.md) Phase 4 — codename *Nooks*.
- `openspec/specs/korner-domain/spec.md` — `Nook`, `Korner`, `KornerDetail` definitions introduced in P3.
- `openspec/changes/phase-4-nooks-tab/proposal.md` — capability rationale.

## Requirements

### Requirement: NookDetail aggregate is composed from Nook and Korner
The `NookDetail` `@Serializable` data class SHALL live at `core/model/NookDetail.kt` and SHALL aggregate a `nook: Nook` field plus a `korner: Korner` field. Both fields SHALL be required (non-nullable). The aggregate is the single payload returned by `NooksRepository.fetchNookDetail(nookId: String)` and rendered by the nook detail screen.

#### Scenario: NookDetail data class exposes required fields
- **WHEN** `NookDetail(nook = ..., korner = ...)` is constructed
- **THEN** both `nook` and `korner` are non-nullable and accessible as Kotlin properties
- **AND** the class is annotated `@Serializable` for future cache/locale plumbing

#### Scenario: NookDetail is shared across feature modules
- **WHEN** `feature/nooks` deserializes a `NookDetail` from a `NooksRepository.fetchNookDetail` result
- **THEN** the same data class is used by the nook detail screen and (indirectly) by the back-to-Korner row tap that routes to `kornerz/{parentKornerId}`

### Requirement: NooksRepository surfaces NookDetail from the same Firestore paths as P3
The `NooksRepository.fetchNookDetail(nookId: String): Flow<NookDetail>` method SHALL resolve the parent Korner by reading `communities/{kornerId}` for the nook's parent community (the lookup mirrors iOS `NookDetailViewModel.swift`). The method SHALL hide the two-step read (nook first, then parent Korner) behind a single repository call so consumers do not need to compose Firestore paths themselves.

#### Scenario: Repository fetches the nook then resolves its parent Korner
- **WHEN** `NooksRepository.fetchNookDetail("n1")` is invoked for a nook whose parent community is `c1`
- **THEN** the repository reads the `Nook` document containing `nookId = "n1"`
- **AND** reads the parent `Korner` document from `communities/c1`
- **AND** emits a `NookDetail(nook = ..., korner = ...)` aggregate
- **AND** surfaces `Result.failure(NotFound)` if either read resolves to a missing document

#### Scenario: Repository preserves the iOS two-read shape
- **WHEN** the iOS `NookDetailViewModel.fetchData()` calls its two sequential Firestore reads
- **THEN** the Android implementation performs the equivalent two reads on the same collection paths
- **AND** does not introduce new backend indexes, collection group queries, or schema changes
