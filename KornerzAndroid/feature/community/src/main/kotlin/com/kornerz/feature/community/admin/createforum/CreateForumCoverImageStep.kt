package com.kornerz.feature.community.admin.createforum

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.community.R

/**
 * Step 3 of the create-forum wizard — the compulsory cover image.
 *
 * 1:1 port of iOS `AddForumCoverImageScreen`
 * (`kornerz/Kornerz/HomeVC/AddForum/AddForumCoverImageScreen.swift`).
 *
 * iOS shows the bold "Choose cover image for replay" heading, a
 * full-width 150 dp preview with a 25 dp corner radius, and the
 * `Image-upload` artwork when nothing is selected yet. Tapping the
 * preview opens an action sheet offering "Photo Library" and "Camera";
 * the library path uses `PhotosPicker` and the camera path opens a
 * `UIImagePickerController` full-screen cover. The circular next arrow
 * floats bottom-trailing and stays disabled until an image is chosen.
 */
@Composable
fun CreateForumCoverImageStep(
    imageUri: Uri?,
    onImagePicked: (Uri) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSourceDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onImagePicked(uri)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            bitmap.toCacheUri(context)?.let(onImagePicked)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.space12),
    ) {
        Spacer(modifier = Modifier.height(Spacing.space8))
        CreateForumSectionHeading(
            text = stringResource(R.string.create_forum_choose_cover_image)
        )

        Spacer(modifier = Modifier.height(Spacing.space12))
        CoverImagePreview(
            imageUri = imageUri,
            onClick = { showSourceDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
        )

        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            CreateForumNextButton(
                enabled = imageUri != null,
                onClick = onNext,
            )
        }
    }

    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text(text = stringResource(R.string.create_forum_select_an_image)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showSourceDialog = false
                            pickImageLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                    ) {
                        Text(text = stringResource(R.string.create_forum_photo_library))
                    }
                    TextButton(
                        onClick = {
                            showSourceDialog = false
                            takePictureLauncher.launch(null)
                        },
                    ) {
                        Text(text = stringResource(R.string.create_forum_camera))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSourceDialog = false }) {
                    Text(text = stringResource(R.string.create_forum_cancel))
                }
            },
        )
    }
}

/**
 * The cover-image preview.
 *
 * iOS renders the selected image with `.scaledToFill()` inside a
 * 150 dp-tall frame with a 25 dp corner radius, and falls back to the
 * `Image-upload` artwork when nothing is selected
 * (`AddForumCoverImageScreen.swift:83-105`).
 */
@Composable
private fun CoverImagePreview(
    imageUri: Uri?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(25.dp)
    val borderColor = MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .drawWithContent {
                drawContent()
                if (imageUri == null) {
                    drawRoundRect(
                        color = borderColor,
                        size = size,
                        cornerRadius = CornerRadius(25.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = stringResource(R.string.create_forum_cd_cover_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_create_forum_image_upload),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
