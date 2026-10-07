package com.kornerz.core.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Wraps `ActivityResultContracts.PickVisualMedia` (architecture §5).
 *
 * The hosting screen is responsible for invoking [launchPicker] from a
 * `KornerzButton` so the picker can be reused with different copy
 * ("Choose from gallery", "Take photo", etc.).
 */
@Composable
fun rememberKornerzImagePicker(
    onPhotoPicked: (Uri) -> Unit,
): ImagePickerHandle {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onPhotoPicked(uri)
    }
    return remember(launcher) { ImagePickerHandle(launcher::launch) }
}

fun interface ImagePickerHandle {

    fun launch(request: PickVisualMediaRequest)
}

/** Default circular avatar placeholder / preview. */
@Composable
fun KornerzAvatar(
    previewUri: Uri?,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 96.dp,
) {
    Surface(
        modifier = modifier.size(size),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (previewUri != null) {
                AsyncImage(
                    model = previewUri,
                    contentDescription = "Profile photo",
                    modifier = Modifier.size(size),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "No photo",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") mutableStateOf(previewUri)
}