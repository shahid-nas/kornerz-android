package com.kornerz.feature.videocall.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
 * Request `CAMERA` + `RECORD_AUDIO` before invoking the connect flow.
 * On denial the screen renders an inline error card with a "Grant
 * permissions" button that either re-launches the request or
 * deep-links to system app-info on "Don't ask again" (design D5).
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
    var permissionsGranted by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        permissionsGranted = results.values.all { it }
        if (!permissionsGranted) {
            permanentlyDenied = !results.values.any { it } &&
                results.none { it.value }
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(
            arrayOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO,
            ),
        )
        viewModel.navEvents.collect { event -> onNavEvent(event) }
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
                launcher.launch(
                    arrayOf(
                        android.Manifest.permission.CAMERA,
                        android.Manifest.permission.RECORD_AUDIO,
                    ),
                )
            }
        },
        modifier = modifier,
    )
}