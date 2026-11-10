package com.kornerz.feature.shell

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Requests the `POST_NOTIFICATIONS` runtime permission on Android 13+.
 *
 * **Why this matters:** on Android 13 (API 33) and above, an app that
 * has not been granted `POST_NOTIFICATIONS` cannot display **any**
 * notification — the FCM payload is delivered, but the system drops it.
 * The permission is declared in the manifest, but a runtime permission
 * has to be granted by the user before it takes effect.
 *
 * The request is fired **once per process**, on the shell's first
 * composition. The shell is only composed for a signed-in user, so the
 * prompt appears in a context where notifications actually make sense.
 *
 * A denial is not fatal: FCM still delivers the payload, the app still
 * shows the notification in the in-app list, and the user can grant the
 * permission later from the system settings. The result of the prompt is
 * therefore informational only.
 *
 * The no-op on Android < 13 is intentional — the permission did not exist
 * before API 33 and notifications were enabled by default.
 */
@Composable
fun RequestPostNotificationsPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var alreadyRequested by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result is informational only — FCM display falls back to
         * in-app cards if the user denies. */ }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted && !alreadyRequested) {
            alreadyRequested = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
