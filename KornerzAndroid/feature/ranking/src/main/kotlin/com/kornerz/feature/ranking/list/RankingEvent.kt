package com.kornerz.feature.ranking.list

/** The user actions the Klouders tab emits (architecture §2.1). */
sealed interface RankingEvent {
    /** The shared header's avatar was tapped. */
    data object AvatarTapped : RankingEvent

    /** The leaderboard's error state asked for a retry. */
    data object Retry : RankingEvent
}
