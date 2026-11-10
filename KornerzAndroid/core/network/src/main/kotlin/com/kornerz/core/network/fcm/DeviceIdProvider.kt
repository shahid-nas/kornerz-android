package com.kornerz.core.network.fcm

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A stable per-install identifier for this device.
 *
 * The FCM token alone cannot identify a *device*: the token rotates, and
 * two devices on the same account each hold their own. The `devices`
 * subcollection therefore needs a device key that stays stable across
 * token rotations.
 *
 * `Settings.Secure.ANDROID_ID` is the platform's per-install id — it
 * survives app updates and token rotations and is unique per app
 * signing key and user on the device. A random UUID is generated and
 * persisted as a fallback for the rare case where the platform id is
 * unavailable.
 */
@Singleton
class DeviceIdProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val fallbackId: String by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).let { prefs ->
            prefs.getString(KEY_FALLBACK_ID, null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY_FALLBACK_ID, it).apply()
            }
        }
    }

    /**
     * The device's stable identifier. Safe to call from any thread.
     */
    suspend fun deviceId(): String = withContext(Dispatchers.IO) {
        runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallbackId
    }

    private companion object {
        const val PREFS_NAME = "kornerz_device"
        const val KEY_FALLBACK_ID = "fallback_device_id"
    }
}
