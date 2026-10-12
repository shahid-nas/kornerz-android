package com.kornerz.kornerz.fcm

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kornerz.core.model.User
import com.kornerz.core.model.UserNotification
import com.kornerz.feature.notifications.data.NotificationRepository
import com.kornerz.feature.profile.data.UserProfileRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FCM service for the Kornerz app. Handles three flows (P6, task 8.3):
 *  - `onNewToken(token)` writes the new token to `Users/{userCode}
 *    .tokenPush` via [UserProfileRepository.updateFcmToken].
 *  - `onMessageReceived(remoteMessage)` displays the bundled
 *    `notification` payload via [NotificationManagerCompat] and
 *    pushes data payloads into [NotificationRepository]'s in-memory
 *    cache for the notifications screen.
 *
 * Deep-link routing from a tapped notification is deferred to P10
 * (per `phases.md` P10 line 314).
 */
@AndroidEntryPoint
class KornerzFcmService : FirebaseMessagingService() {

    @Inject lateinit var userRepository: UserProfileRepository
    @Inject lateinit var notificationRepository: NotificationRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            val user: User? = userRepository.observeSessionUser()
                .firstOrNull { it != null && it.email.isNotEmpty() }
            if (user != null) {
                userRepository.updateFcmToken(user.userCode ?: user.email, token)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val notification = remoteMessage.notification
        if (notification != null) {
            val tapIntent = packageManager.getLaunchIntentForPackage(packageName)
            val pi = tapIntent?.let {
                android.app.PendingIntent.getActivity(
                    this, 0, it,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(com.kornerz.core.ui.R.drawable.ic_stat_kornerz)
                .setContentTitle(notification.title)
                .setContentText(notification.body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
            if (pi != null) builder.setContentIntent(pi)
            NotificationManagerCompat.from(this)
                .notify(NEXT_NOTIFICATION_ID++, builder.build())
        }
        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val userNotification = UserNotification(
                id = data["notificationId"].orEmpty(),
                title = data["notificationTitle"],
                description = data["notificationDescription"],
                content = data["notificationContent"],
                image = data["notificationCommunityImage"],
                date = data["dateNotification"]?.toLongOrNull()?.let {
                    kotlinx.datetime.Instant.fromEpochMilliseconds(it * 1000)
                },
                reference = data["notificationReferenceId"],
                type = data["notificationType"],
                userId = data["notificationUserId"],
                communityId = data["notificationCommunityId"],
            )
            if (userNotification.id.isNotEmpty()) {
                notificationRepository.pushDataMessage(userNotification)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "kornerz_notifications"
        private var NEXT_NOTIFICATION_ID = 1000
    }
}
