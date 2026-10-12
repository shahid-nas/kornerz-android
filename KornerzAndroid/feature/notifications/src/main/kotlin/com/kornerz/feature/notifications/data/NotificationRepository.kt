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

    fun pushDataMessage(notification: UserNotification) {
        _recentDataMessages.value = listOf(notification) + _recentDataMessages.value
    }
}
