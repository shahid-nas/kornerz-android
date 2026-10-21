package com.kornerz.feature.community.admin.detail

import com.kornerz.core.model.Community

/**
 * Read-only stats projection for `CommunityDetailAsAdmin`.
 * Mirrors iOS `OwnedCommunityViewModel` (the seven counts shown on the
 * `CommunityDetailAsAdminVC` stats dashboard).
 *
 * All counts are derived from server-side Firestore `count()`
 * aggregations against the matching sub-collection, so only the
 * integer count is transferred per call. See
 * [CommunityStatsFirestoreApi] for the exact `whereField`s.
 *
 * `talkingNow` is hard-coded to `0` on both platforms — iOS lines
 * 186–191 in `CommunityDetailAsAdminVC.setupUI()` set the value to
 * "0" without firing any read; Android mirrors that behavior.
 */
data class CommunityStats(
    val community: Community,
    val members: Int = 0,
    val talkingNow: Int = 0,
    val events: Int = 0,
    val articles: Int = 0,
    val videos: Int = 0,
    val forums: Int = 0,
    val notifications: Int = 0,
    val pendingEventRequests: Int = 0,
)
