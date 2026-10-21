# firebase-storage-upload Specification

## Purpose
TBD - created by archiving change phase-7-create-community-and-admin. Update Purpose after archive.

## Status: CANCELLED after iOS audit

The `CommunityStorageApi.uploadCover(...)` v4 signed-URL upload helper
was scoped as the second concrete consumer of the v4 pipeline (the
first being P6's `UserStorageApi.uploadAvatar`). After the iOS audit
showed that iOS does not surface a Create Community flow, the
`CommunityStorageApi` had no consumer and was removed from the
Android port.

The P6 `UserStorageApi.uploadAvatar` remains the sole v4 signed-URL
consumer in the codebase. Future phases that need a cover / image
upload helper can reintroduce the `CommunityStorageApi` (or a more
general `MediaStorageApi`) following the same `StorageSignedUrlClient`
+ `runCatching` pattern.

The P7 proposal's `## ADDED Requirements` in this spec file are
treated as cancelled. The P6 `firebase-storage-avatar-upload` spec
is the canonical reference for the v4 upload pattern.
