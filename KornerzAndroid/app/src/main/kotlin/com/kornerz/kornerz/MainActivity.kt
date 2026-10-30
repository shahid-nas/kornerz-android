package com.kornerz.kornerz

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.kornerz.core.model.DeepLink
import com.kornerz.core.network.videocall.VonageSessionController
import com.kornerz.core.storage.country.CountryCodesRepository
import com.kornerz.core.storage.interests.KornerInterestRepository
import com.kornerz.core.ui.device.DeviceFormFactor
import com.kornerz.core.ui.theme.KornerzTheme
import com.kornerz.feature.shell.deeplink.DeepLinkManager
import com.kornerz.kornerz.fcm.KornerzFcmService
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
        fun kornerInterestRepository(): KornerInterestRepository
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
        val channel = NotificationChannelCompat.Builder(
            KornerzFcmService.CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_HIGH,
        )
            .setName("Kornerz")
            .setDescription("Kornerz push notifications")
            .setLightsEnabled(true)
            .setVibrationEnabled(true)
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)

        val entry = EntryPointAccessors.fromApplication(
            applicationContext,
            RepositoryEntryPoint::class.java,
        )
        val countryRepository = entry.countryCodesRepository()
        val interestsRepository = entry.kornerInterestRepository()

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
                    interestsRepository = interestsRepository,
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
     * P10 (task 9.5) — the **warm** path: a `kornerz://` intent arrived
     * while the activity was already running.
     *
     * `MainActivity` is `singleTop`, so the intent is delivered here
     * instead of creating a second activity instance. `setIntent`
     * keeps the activity's current intent accurate for a later
     * configuration change or process restore.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    /**
     * Hands a `kornerz://` URI to the deep-link manager. Anything else
     * — a null intent, a foreign scheme, a URI that does not match the
     * grammar — is ignored.
     */
    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (!uri.scheme.equals(DeepLink.SCHEME, ignoreCase = true)) return
        val deeplink = DeepLink.parse(uri.toString()) ?: return
        deepLinkManager.handle(deeplink)
    }
}