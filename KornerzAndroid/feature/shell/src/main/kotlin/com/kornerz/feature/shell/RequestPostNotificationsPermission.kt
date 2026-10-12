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
 * P6 (task 8.6) — Requests the `POST_NOTIFICATIONS` runtime
 * permission on Android 13+. Invoked from the shell's auth graph
 * entry; the no-op on Android < 13 is intentional.
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
