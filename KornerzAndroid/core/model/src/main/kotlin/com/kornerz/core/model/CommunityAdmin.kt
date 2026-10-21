package com.kornerz.core.model

/**
 * The admin-owned-community projection. Bundles a [community] with
 * its live `memberCount` + `nookCount` (P7, `community-domain` spec).
 * Used by the `community/admin/list` screen to render the
 * `Members`/`Nooks` count pills.
 */
data class CommunityAdmin(
    val community: Community,
    val memberCount: Int,
    val nookCount: Int,
) {
    companion object {
        fun from(community: Community, memberCount: Int, nookCount: Int): CommunityAdmin =
            CommunityAdmin(
                community = community,
                memberCount = memberCount,
                nookCount = nookCount,
            )
    }
}
