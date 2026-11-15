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
import com.kornerz.core.ui.DeepLinkLog
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
 *
 * **The foreground path and the backgrounded path are different
 * mechanisms.** This service only runs while the app is in the
 * foreground, so the notification it builds is the only one it can
 * give a tap intent to. When the app is backgrounded or killed, the
 * Firebase SDK displays the notification itself and this service never
 * runs — the SDK then launches the app from the payload's
 * `click_action`, and the payload arrives in the launch intent's
 * extras. `MainActivity` reads both transports; see
 * `MainActivity.resolveDeepLink`.
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
            Log.i(TAG, "FCM token (Rotated in FCM service) deviceId=$deviceId token=$token")
            deviceTokenStore.upsert(email = user.email, deviceId = deviceId, token = token)
                .onSuccess { Log.i(TAG, "FCM token (Registered for ${user.email}) token=$token") }
                .onFailure { Log.w(TAG, "Could not write the rotated FCM token", it) }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // The payload's shape is the backend's contract, and this repo
        // cannot verify it. Logging it is what makes a mismatch between
        // the app and the backend visible instead of silent.
        Log.i(TAG, "FCM message: data=${remoteMessage.data}")
        DeepLinkLog.log(
            "1-payload",
            "message received in the FOREGROUND (the app built this " +
                "notification itself). data=${remoteMessage.data} " +
                "notification=${remoteMessage.notification != null}",
        )
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
                id = data["notificationId"].orEmpty().ifBlank {
                    // The backend does not always send a
                    // `notificationId` — the push dialect has no
                    // equivalent. Without an id the record cannot be
                    // written (the `Notifications` collection is keyed
                    // by it), so a deterministic id is minted from the
                    // payload's own contents. The same payload yields
                    // the same id, so a redelivery updates the same
                    // record instead of duplicating it.
                    derivedNotificationId(data)
                },
                title = data["notificationTitle"] ?: notification?.title,
                description = data["notificationDescription"] ?: notification?.body,
                content = data["notificationContent"],
                image = data["notificationCommunityImage"],
                date = data["dateNotification"]?.toLongOrNull()?.let {
                    kotlinx.datetime.Instant.fromEpochMilliseconds(it * 1000)
                },
                reference = data["notificationReferenceId"] ?: pushDialectReference(data),
                type = data["notificationType"] ?: data["pushType"],
                userId = data["notificationUserId"],
                communityId = data["notificationCommunityId"] ?: data["communityId"],
            )
            DeepLinkLog.log(
                "1-payload",
                "in-app notification built: id=${userNotification.id} " +
                    "type=${userNotification.type} reference=${userNotification.reference} " +
                    "communityId=${userNotification.communityId}",
            )
            if (userNotification.id.isNotEmpty()) {
                scope.launch {
                    notificationRepository.pushDataMessage(userNotification)
                    DeepLinkLog.log(
                        "1-payload",
                        "in-app notification stored: id=${userNotification.id}",
                    )
                }
            }
        } else {
            DeepLinkLog.skip(
                "1-payload",
                "the message carried no data payload — there is no deep " +
                    "link to build. The notification will open the app " +
                    "without a destination.",
            )
        }
    }

    /**
     * A deterministic notification id for a payload that carries no
     * `notificationId`.
     *
     * The `Notifications` collection is keyed by `notificationId`, so a
     * record cannot be written without one. The id is derived from the
     * payload's type, its reference and its date — the three fields
     * that identify *which* notification this is — so the same
     * notification redelivered by FCM resolves to the same record
     * instead of writing a second copy.
     */
    private fun derivedNotificationId(data: Map<String, String>): String {
        val type = data["notificationType"] ?: data["pushType"].orEmpty()
        val reference = data["notificationReferenceId"]
            ?: pushDialectReference(data).orEmpty()
        val date = data["dateNotification"].orEmpty()
        return "push-$type-$reference-$date"
    }

    /**
     * The entity id a **push-dialect** payload names.
     *
     * The backend describes a notification in one of two dialects. The
     * **record** dialect — `notificationType` / `notificationReferenceId`
     * / `notificationCommunityId` — is the one the `Notifications`
     * collection is written in, and it is preferred. The **push**
     * dialect — `pushType` / `communityId` / `eventId` / `articleId` /
     * `forumId` / `loopId` — is the one the deep link is built from,
     * and it is the fallback.
     *
     * A push that carries only the push dialect would otherwise never
     * become an in-app notification: the record dialect's
     * `notificationReferenceId` and `notificationType` are absent, so
     * the notification would have no reference to open and no type to
     * route on. Reading the push dialect's own ids closes that gap.
     *
     * The id is the one the push type names: a loop comment names the
     * loop, a forum comment names the forum, and the community-scoped
     * types name their entity in the community.
     */
    private fun pushDialectReference(data: Map<String, String>): String? =
        when (data["pushType"]?.lowercase()) {
            "loopcomment", "newloop" -> data["loopId"]
            "newcomment" -> data["forumId"]
            "newevent" -> data["eventId"]
            "newarticle" -> data["articleId"]
            "newforum" -> data["forumId"]
            else -> null
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
     * The intent carries the link **twice**: as the `kornerz://` URI in
     * `intent.data`, and as the payload in `intent.extras`. The URI is
     * what the app's own deep-link filter matches; the extras are what
     * the Firebase SDK's launch intent carries in the backgrounded /
     * killed case. Reading both means the two transports cannot drift
     * apart — they are built from the same payload, by the same
     * parser, in the same place.
     *
     * A payload with no recognisable `pushType` falls back to the plain
     * launch intent, so the notification still opens the app.
     */
    private fun buildTapIntent(payload: Map<String, String>): PendingIntent? {
        val deeplink = DeepLinkPayloadParser.parseAny(payload)
        val intent = if (deeplink != null) {
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse(deeplink.toUri())
                putExtra(DeepLinkPayloadParser.CLICK_ACTION, true)
                payload.forEach { (key, value) -> putExtra(key, value) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        } else {
            DeepLinkLog.skip(
                "2-tap-intent",
                "the payload could NOT be parsed into a deep link, so the " +
                    "notification falls back to the plain launch intent — " +
                    "tapping it opens the app WITHOUT a destination. " +
                    "payload=$payload. Check that the payload carries a " +
                    "recognised pushType (or notificationType) and the " +
                    "id that type needs (loopId, forumId, eventId, " +
                    "articleId, communityId).",
            )
            packageManager.getLaunchIntentForPackage(packageName)
        }
        intent?.let {
            DeepLinkLog.log(
                "2-tap-intent",
                "tap intent built: uri=${it.data} action=${it.action} " +
                    "link=$deeplink extras=${payload.keys}",
            )
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
