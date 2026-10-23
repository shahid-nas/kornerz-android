package com.kornerz.kornerz.fcm

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kornerz.core.model.DeepLink
import com.kornerz.core.model.DeepLinkPayloadParser
import com.kornerz.core.model.User
import com.kornerz.core.model.UserNotification
import com.kornerz.feature.notifications.data.NotificationRepository
import com.kornerz.feature.profile.data.UserProfileRepository
import com.kornerz.kornerz.MainActivity
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
 *  - a tapped tray notification carries the deep link built from the
 *    **push** payload, so the tap opens the app *and* routes to the
 *    resolved destination (P10, task 9.7).
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
            val pi = buildTapIntent(remoteMessage.data)
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

    /**
     * Builds the tray notification's tap intent.
     *
     * P10 (task 9.7): the intent carries the deep link derived from the
     * **push** payload — the dialect iOS reads in
     * `DeepLinksManager.handleDeepLink(info:)` (`:85-134`) — so tapping
     * the notification opens the app *and* routes to the resolved
     * destination. iOS does the same: `AppDelegate.userNotificationCenter
     * (_:didReceive:)` (`:170-175`) passes the tapped notification's
     * `userInfo` straight to `DeepLinksManager.shared.handleDeepLink(info:)`.
     *
     * A payload with no recognisable `pushType` falls back to the plain
     * launch intent, so the notification still opens the app.
     */
    private fun buildTapIntent(payload: Map<String, String>): PendingIntent? {
        val deeplink = DeepLinkPayloadParser.parse(payload)
        val intent = if (deeplink != null) {
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse(deeplink.toUri())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        } else {
            packageManager.getLaunchIntentForPackage(packageName)
        }
        return intent?.let {
            PendingIntent.getActivity(
                this, NEXT_NOTIFICATION_ID, it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "kornerz_notifications"
        private var NEXT_NOTIFICATION_ID = 1000
    }
}
