package com.kornerz.feature.notifications.list

import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.DeepLink
import com.kornerz.core.model.UserNotification

/**
 * A notification list row.
 */
data class UserNotificationRow(
    val notification: UserNotification,
)

data class NotificationsState(
    val items: AsyncValue<List<UserNotificationRow>> = AsyncValue.Loading,
    val isLoadingMore: Boolean = false,
    val hasMoreData: Boolean = true,
    val paginationError: Throwable? = null,
)

sealed interface NotificationsEvent {
    data object Refresh : NotificationsEvent
    data class NotificationOpened(val notification: UserNotification) : NotificationsEvent
}

sealed interface NotificationsNavEvent {
    data class OpenLoopComment(val loopId: String) : NotificationsNavEvent
    data class OpenForumComment(val forumId: String) : NotificationsNavEvent

    /**
     * A resolved deep link. The shell routes it through the
     * `DeepLinkResolver` — P10 — instead of showing the P6 placeholder.
     */
    data class Open(val deeplink: DeepLink) : NotificationsNavEvent
    data class ShowAlert(val message: String) : NotificationsNavEvent
}
