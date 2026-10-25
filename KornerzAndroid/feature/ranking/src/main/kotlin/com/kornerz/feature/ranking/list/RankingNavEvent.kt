package com.kornerz.feature.ranking.list

/**
 * The navigation side-channel of the Klouders tab (architecture §2.4).
 * The shell owns the `NavController`; the tab only reports intent.
 */
sealed interface RankingNavEvent {
    /** Opens the signed-in user's profile — the avatar tap. */
    data object NavigateToProfile : RankingNavEvent
}
