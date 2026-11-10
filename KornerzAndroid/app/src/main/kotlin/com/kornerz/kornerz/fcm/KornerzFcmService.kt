package com.kornerz.kornerz.fcm

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kornerz.core.model.DeepLink
import com.kornerz.core.model.DeepLinkPayloadParser
import com.kornerz.core.model.User
import com.kornerz.core.model.UserNotification
import com.kornerz.core.network.fcm.DeviceIdProvider
import com.kornerz.core.network.fcm.DeviceTokenStore
import com.kornerz.feature.notifications.data.NotificationRepository
import com.kornerz.feature.profile.data.UserProfileRepository
import com.kornerz.kornerz.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * FCM service for the Kornerz app. Handles three flows (P6, task 8.3):
 *  - `onNewToken(token)` writes the new token to `Users/{email}
 *    .tokenPush` via [DeviceTokenStore].
 *  - `onMessageReceived(remoteMessage)` displays the bundled
 *    `notification` payload via [NotificationManagerCompat] and
 *    records data payloads in the `Notifications` collection (and in
 *    [NotificationRepository]'s in-memory cache) for the notifications
 *    screen.
 *  - a tapped tray notification carries the deep link built from the
 *    **push** payload, so the tap opens the app *and* routes to the
 *    resolved destination (P10, task 9.7).
 */
@AndroidEntryPoint
class KornerzFcmService : FirebaseMessagingService() {

    @Inject lateinit var userRepository: UserProfileRepository
    @Inject lateinit var notificationRepository: NotificationRepository
    @Inject lateinit var deviceTokenStore: DeviceTokenStore
    @Inject lateinit var deviceIdProvider: DeviceIdProvider

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * A token rotation arrives whenever the token is refreshed — which
     * can happen while the user is signed out. The token is written when
     * a session is available; when there is none it is **held** and
     * uploaded by the next registration, so a rotation is never lost.
     */
    override fun onNewToken(token: String) {
        scope.launch {
            val user: User? = userRepository.observeSessionUser()
                .firstOrNull { it != null && it.email.isNotEmpty() }
            if (user == null) {
                Log.i(TAG, "No session for the rotated FCM token; the registrar will upload it")
                return@launch
            }
            val deviceId = deviceIdProvider.deviceId()
            deviceTokenStore.upsert(email = user.email, deviceId = deviceId, token = token)
                .onFailure { Log.w(TAG, "Could not write the rotated FCM token", it) }
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
                .notify(NEXT_NOTIFICATION_ID.incrementAndGet(), builder.build())
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
                scope.launch {
                    notificationRepository.pushDataMessage(userNotification)
                }
            }
        }
    }

    /**
     * Called when FCM drops messages because the token is no longer
     * valid — the app was uninstalled, or the token was revoked.
     *
     * The token is cleared from Firestore so the backend stops trying
     * to target it. A fresh token is minted by the SDK and delivered
     * through [onNewToken], which re-registers it.
     */
    override fun onDeletedMessages() {
        Log.w(TAG, "FCM deleted messages; the token is no longer valid")
        scope.launch {
            val user: User? = userRepository.observeSessionUser()
                .firstOrNull { it != null && it.email.isNotEmpty() }
            if (user == null) return@launch
            val deviceId = deviceIdProvider.deviceId()
            deviceTokenStore.clearDevice(email = user.email, deviceId = deviceId)
                .onFailure { Log.w(TAG, "Could not clear the invalidated FCM token", it) }
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
            // The request code must differ from the notification's own
            // id: `PendingIntent` equality ignores the extras and the
            // intent's action, so two notifications that resolve to the
            // same activity would otherwise share one `PendingIntent` —
            // and the second tap would re-fire the first notification's
            // deep link.
            PendingIntent.getActivity(
                this, NEXT_NOTIFICATION_ID.get(), it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "kornerz_notifications"
        private const val TAG = "KornerzFcmService"

        /**
         * Monotonic notification ids. An `AtomicInteger` rather than a
         * plain `var` because `onMessageReceived` can be called on more
         * than one thread, and two concurrent calls could otherwise
         * mint the same id.
         */
        private val NEXT_NOTIFICATION_ID = AtomicInteger(1000)
    }
}
