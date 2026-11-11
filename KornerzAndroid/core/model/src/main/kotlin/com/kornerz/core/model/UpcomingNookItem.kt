package com.kornerz.core.model

import kotlinx.serialization.Serializable

/**
 * Per-item projection for the Nooks tab "Upcoming" section. Mirrors
 * the iOS `NookCommunityWrapper` shape used in
 * `NooksTabViewModel.fetchUpcommingNooks()`:
 *  - [nook] — the Event (`Events/{id}` doc)
 *  - [publisher] — the User that published the nook, looked up by
 *    `Events.{nook.publisherId}` → `Users.{userCode}`
 *  - [parentKorner] — the parent Korner, looked up by
 *    `Events.{nook.communityId}` → `Communities.{communityId}` field
 *  - [sessionInfo] — the live roster of the nook's room, resolved from
 *    the `getSessionsInfo` Cloud Function. Drives the "X spots left"
 *    line. Mirrors iOS `NookCommunityWrapper.sessionInfo`.
 *
 * Publisher is optional because iOS silently drops events whose
 * publisher is missing (see `NookCommunityWrapper` build loop).
 */
@Serializable
data class UpcomingNookItem(
    val nook: Nook,
    val publisher: User?,
    val parentKorner: Korner,
    val sessionInfo: SessionInfo? = null,
)
