package com.kornerz.feature.notifications.data

import com.google.firebase.firestore.DocumentSnapshot
import com.kornerz.core.model.UserNotification
import com.kornerz.feature.notifications.data.network.NotificationFirestoreApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for the `Notifications` collection. Wraps
 * [NotificationFirestoreApi] and exposes an in-memory
 * `recentDataMessages` cache for FCM data-payload injection
 * (P6, task 4.3 — `notifications-push-display` spec).
 *
 * **The in-memory cache is a cache, not the record.** Every pushed
 * data message is also written to the `Notifications` collection, so a
 * notification that arrives while the process was dead — or that is
 * delivered while the app is in the background, where FCM handles the
 * display itself and never calls `onMessageReceived` — is still in the
 * list the next time the screen is opened.
 */
@Singleton
class NotificationRepository @Inject constructor(
    private val api: NotificationFirestoreApi,
) {

    private val _recentDataMessages =
        MutableStateFlow<List<UserNotification>>(emptyList())
    val recentDataMessages: StateFlow<List<UserNotification>> =
        _recentDataMessages.asStateFlow()

    private var cursor: DocumentSnapshot? = null

    suspend fun fetchNextPage(userCode: String): Result<Pair<List<UserNotification>, Boolean>> =
        runCatching {
            val (page, nextCursor) = api.fetchPage(userCode, cursor).getOrThrow()
            cursor = nextCursor
            // No more pages when the page is smaller than the limit
            // (i.e. fewer than 10 items returned).
            val hasMore = page.size >= 10
            page to hasMore
        }

    suspend fun reset() {
        cursor = null
    }

    /**
     * Records a notification delivered through an FCM data payload.
     *
     * The notification is written to the `Notifications` collection
     * **and** pushed into the in-memory cache, so it shows up in the
     * list immediately and survives a process death.
     */
    suspend fun pushDataMessage(notification: UserNotification) {
        api.persist(notification)
        _recentDataMessages.value =
            listOf(notification) + _recentDataMessages.value.filterNot { it.id == notification.id }
    }

    fun clearRecentDataMessages() {
        _recentDataMessages.value = emptyList()
    }
}
