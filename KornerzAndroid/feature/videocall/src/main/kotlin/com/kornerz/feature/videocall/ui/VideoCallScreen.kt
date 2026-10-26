package com.kornerz.feature.videocall.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kornerz.feature.videocall.VideoCallEvent
import com.kornerz.feature.videocall.VideoCallNavEvent
import com.kornerz.feature.videocall.VideoCallViewModel

/**
 * Entry Composable for the video call screen (architecture §2.1,
 * §2.4). Pulls `sessionId` from the `SavedStateHandle` so a single
 * `hiltViewModel()` instance is shared per `sessionId` deep-link.
 *
 * Request `CAMERA` + `RECORD_AUDIO` before invoking the connect flow
 * (iOS asks for camera/microphone access before opening the session
 * too). The grant result is forwarded to the view model, which only
 * opens the Vonage session once the permissions are in place. On denial
 * the screen renders an inline card with a "Grant permissions" button
 * that either re-launches the request or deep-links to system
 * app-info on "Don't ask again" (design D5).
 *
 * Navigation events are surfaced via `onNavEvent` so the shell
 * route graph (`AppShell`) can resolve them — the screen never
 * references `NavController` directly (architecture §10).
 */
@Composable
fun VideoCallScreen(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    viewModel: VideoCallViewModel = hiltViewModel(),
    onNavEvent: (VideoCallNavEvent) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val packageName = context.packageName
    var permissionsGranted by remember { mutableStateOf<Boolean?>(null) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results.values.all { it }
        permissionsGranted = granted
        if (!granted) {
            // "Don't ask again" leaves the system dialog unshowable, so
            // the only way forward is the app-info screen.
            val activity = context.findActivity()
            permanentlyDenied = activity != null && requiredPermissions().all { permission ->
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            }
        }
        viewModel.onEvent(VideoCallEvent.PermissionsResult(granted))
    }

    LaunchedEffect(Unit) {
        launcher.launch(requiredPermissions())
    }

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event -> onNavEvent(event) }
    }

    // iOS keeps the screen awake for the duration of the call
    // (`UIApplication.shared.isIdleTimerDisabled = true`).
    val activity = context.findActivity()
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // The SDK builds its `Publisher` / `Subscriber` with a `Context`,
    // and the renderer is a `SurfaceView` — it resolves its display and
    // its window from that context. The **Activity** context is what
    // makes the video land on the call screen's own window; with the
    // application context the surface is bound to the default display
    // and the video is drawn in the wrong place, at the wrong size.
    LaunchedEffect(activity) {
        activity?.let { viewModel.setCallContext(it) }
    }

    VideoCallContent(
        state = state,
        onEvent = viewModel::onEvent,
        permissionsGranted = permissionsGranted,
        onGrantPermissions = {
            if (permanentlyDenied) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                context.startActivity(intent)
            } else {
                launcher.launch(requiredPermissions())
            }
        },
        onReconnect = viewModel::reconnectSession,
        modifier = modifier,
    )
}

private fun requiredPermissions(): Array<String> = arrayOf(
    android.Manifest.permission.CAMERA,
    android.Manifest.permission.RECORD_AUDIO,
)

/**
 * Walks the context chain up to the hosting [Activity] — the
 * permission APIs are Activity-scoped.
 */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var current: android.content.Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is android.app.Activity) return current
        current = current.baseContext
    }
    return null
}
