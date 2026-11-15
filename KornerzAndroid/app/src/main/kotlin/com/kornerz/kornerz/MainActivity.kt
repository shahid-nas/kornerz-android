package com.kornerz.kornerz

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.kornerz.core.model.DeepLink
import com.kornerz.core.ui.DeepLinkLog
import com.kornerz.core.model.DeepLinkPayloadParser
import com.kornerz.core.network.community.CommunitiesRepository
import com.kornerz.core.network.videocall.VonageSessionController
import com.kornerz.core.storage.country.CountryCodesRepository
import com.kornerz.core.ui.device.DeviceFormFactor
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.feature.shell.deeplink.DeepLinkManager
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Single activity host. Edge-to-edge enabled so the bottom-bar
 * respects gesture navigation insets on API 26+.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RepositoryEntryPoint {
        fun countryCodesRepository(): CountryCodesRepository
        fun communitiesRepository(): CommunitiesRepository
    }

    /**
     * P10 — the deep-link manager is a process-scoped `@Singleton`, so
     * the activity reaches it through a Hilt entry point rather than
     * through the composition root.
     *
     * **The entry point must be installed in [SingletonComponent]** — the
     * P8 `PremiumGateEntryPoint` precedent. Installing it in
     * `ActivityComponent` compiles and links fine but throws
     * `ClassCastException` at launch, because the singleton
     * implementation does not implement an activity-scoped entry point.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DeepLinkManagerEntryPoint {
        fun deepLinkManager(): DeepLinkManager
    }

    private lateinit var deepLinkManager: DeepLinkManager

    /**
     * The Vonage session controller.
     *
     * The SDK requires the host activity to forward its lifecycle to
     * the session (the Vonage tutorial's step 5): without
     * `session.onPause()` / `session.onResume()` the SDK keeps its
     * media devices and sockets alive across a backgrounding, and the
     * session comes back connected but no longer receiving the peers'
     * stream events.
     */
    @Inject
    lateinit var vonageSessionController: VonageSessionController

    override fun onPause() {
        super.onPause()
        vonageSessionController.onPause()
    }

    override fun onResume() {
        super.onResume()
        vonageSessionController.onResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyOrientationPolicy()
        // P6 (task 8.5) — register the Kornerz notification channel.
        //
        // The channel is created on **every** launch, not only the first
        // one: `createNotificationChannel` is idempotent for an existing
        // channel and updates its name, description and importance — so
        // a change to the channel's configuration reaches installs that
        // already have the channel. (The system does not let an app
        // change a channel's sound or vibration pattern after creation,
        // but importance and the user-visible labels are updatable.)
        val channel = NotificationChannelCompat.Builder(
            getString(R.string.kornerz_notification_channel_id),
            NotificationManagerCompat.IMPORTANCE_HIGH,
        )
            .setName("Kornerz")
            .setDescription("Kornerz push notifications")
            .setLightsEnabled(true)
            .setVibrationEnabled(true)
            .setShowBadge(true)
            .build()
        NotificationManagerCompat.from(this)
            .createNotificationChannel(channel)

        val entry = EntryPointAccessors.fromApplication(
            applicationContext,
            RepositoryEntryPoint::class.java,
        )
        val countryRepository = entry.countryCodesRepository()
        val communitiesRepository = entry.communitiesRepository()

        deepLinkManager = EntryPointAccessors.fromApplication(
            applicationContext,
            DeepLinkManagerEntryPoint::class.java,
        ).deepLinkManager()

        // P10 (task 9.4) — the **cold** path: the activity was created
        // with a deep-link intent. The manager holds the link until the
        // shell's `NavHost` is composed.
        handleDeepLink(intent)

        setContent {
            KornerzTheme {
                AppRoot(
                    countryRepository = countryRepository,
                    communitiesRepository = communitiesRepository,
                )
            }
        }
    }

    /**
     * Orientation policy: **phones are portrait-only**, tablets may
     * rotate freely.
     *
     * The manifest cannot express this on its own — the
     * `android:screenOrientation` attribute takes an enum, not a
     * resource reference, so there is no way to keep portrait for
     * `sw<600dp` devices and unrestricted orientation for tablets
     * from a single `<activity>` tag. The form factor is therefore
     * resolved from the `sw600dp` resource qualifier
     * (`R.bool.is_tablet`) and applied here.
     *
     * Phones get `SCREEN_ORIENTATION_PORTRAIT`, which also stops the
     * layout from being rebuilt for landscape. Tablets get
     * `SCREEN_ORIENTATION_UNSPECIFIED`, i.e. the platform default
     * (full sensor / user rotation setting).
     */
    private fun applyOrientationPolicy() {
        requestedOrientation = if (DeviceFormFactor.isTablet(this)) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    /**
     * P10 (task 9.5) — the **warm** path: a deep-link intent arrived
     * while the activity was already running.
     *
     * `MainActivity` is `singleTop`, so the intent is delivered here
     * instead of creating a second activity instance. `setIntent`
     * keeps the activity's current intent accurate for a later
     * configuration change or process restore.
     *
     * The same method serves both transports — the app's own
     * `kornerz://` URI and the Firebase SDK's extras-carrying launch
     * intent — because the warm and cold paths are the same two
     * mechanisms, differing only in which lifecycle callback delivers
     * the intent.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    /**
     * Hands a deep link to the deep-link manager.
     *
     * Two transports reach this method, and **both** must be handled:
     *
     *  - **The `kornerz://` URI** — the app's own `PendingIntent`, built
     *    in `KornerzFcmService.buildTapIntent` when the app was in the
     *    foreground and `onMessageReceived` ran.
     *  - **The intent extras** — the launch intent the Firebase SDK
     *    builds when it displays the notification itself, which is what
     *    happens whenever the app was backgrounded or killed. The SDK
     *    copies the message's `data` payload into the extras and the
     *    `click_action` into the intent's action. Without reading the
     *    extras, a tap on a notification that arrived while the app was
     *    not running would open the app but land the user on the Home
     *    tab instead of the screen the notification is about.
     *
     * iOS has no such split: `UNUserNotificationCenterDelegate
     * .didReceive` fires for a tap in every app state and the payload
     * is in `userInfo` either way.
     *
     * Anything else — a null intent, a foreign scheme, a payload with
     * no recognisable `pushType` — is ignored.
     */
    private fun handleDeepLink(intent: Intent?) {
        if (intent == null) {
            DeepLinkLog.skip("3-intent", "the activity received no intent")
            return
        }
        val deeplink = resolveDeepLink(intent)
        if (deeplink == null) {
            DeepLinkLog.skip(
                "3-intent",
                "the intent carried NO usable deep link — the app will " +
                    "open normally and the user lands on the Home tab. " +
                    "action=${intent.action} data=${intent.data} " +
                    "extras=${intent.extras?.keySet()}",
            )
            return
        }
        DeepLinkLog.log("3-intent", "deep link resolved from the intent: $deeplink")
        deepLinkManager.handle(deeplink)
    }

    /**
     * Resolves the deep link an intent carries, or `null` when it
     * carries none.
     *
     * The URI transport is tried first, then the extras transport.
     *
     * The URI transport is tried first because the app's own
     * `PendingIntent` puts the link there — and that intent also
     * carries the payload in its extras, so either transport yields the
     * same link. The Firebase SDK's launch intent, by contrast, may
     * carry the `click_action` URI (`kornerz://deeplink`) as its data;
     * that URI is not part of the link grammar, so it parses to `null`
     * and the extras transport handles it instead.
     */
    private fun resolveDeepLink(intent: Intent): DeepLink? {
        // Every key the intent carries, before the app's own filter
        // narrows them down. The raw key set is what tells you whether
        // the payload arrived at all — the app's `PAYLOAD_KEYS` filter
        // would silently drop a payload whose keys are spelled
        // differently, and the filtered view alone would look empty
        // without showing why.
        DeepLinkLog.log(
            "3-intent",
            "intent received: action=${intent.action} data=${intent.data} " +
                "flags=${intent.flags} extrasKeys=${intent.extras?.keySet()}",
        )

        intent.data?.let { uri ->
            if (uri.scheme?.equals(DeepLink.SCHEME, ignoreCase = true) == true) {
                DeepLink.parse(uri.toString())?.let {
                    DeepLinkLog.log("4-transport", "carried by the URI transport: $it")
                    return it
                }
                DeepLinkLog.skip(
                    "4-transport",
                    "the intent data is a kornerz:// URI but it does not " +
                        "match the link grammar, so it carries no link: $uri",
                )
            } else {
                DeepLinkLog.log(
                    "4-transport",
                    "the intent data is not a kornerz:// URI (scheme=" +
                        "${uri.scheme}) — falling back to the extras " +
                        "transport",
                )
            }
        }
        val payload = DeepLinkPayloadParser.payloadFromExtras(
            intent.extras?.let { bundle ->
                bundle.keySet().associateWith { key -> bundle.get(key) }
            }.orEmpty(),
        )
        // The payload's keys are logged: the backend's payload shape is
        // not something this repo can verify, and a mismatch here is
        // the difference between "the tap does nothing" and a working
        // deep link.
        Log.i(
            TAG,
            "Deep link intent: action=${intent.action} data=${intent.data} " +
                "payload=$payload",
        )
        DeepLinkLog.log(
            "4-transport",
            "carried by the extras transport: rawExtras=" +
                "${intent.extras?.keySet()} filteredPayload=$payload",
        )
        if (payload.isEmpty()) {
            DeepLinkLog.skip(
                "4-transport",
                "the extras carried NO payload keys the app recognises. " +
                    "rawExtras=${intent.extras?.keySet()}. If the " +
                    "backend did not send 'click_action', the Firebase " +
                    "SDK launches the app with a plain launcher intent " +
                    "that has no deep link in it at all.",
            )
        }
        return DeepLinkPayloadParser.parseAny(payload)
    }

    private companion object {
        const val TAG = "KornerzMainActivity"
    }
}