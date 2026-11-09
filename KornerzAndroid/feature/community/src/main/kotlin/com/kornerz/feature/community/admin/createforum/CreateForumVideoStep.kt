package com.kornerz.feature.community.admin.createforum

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.community.R

/**
 * Step 4 of the create-forum wizard — the optional video.
 *
 * 1:1 port of iOS `AddForumVideoScreen`
 * (`kornerz/Kornerz/HomeVC/AddForum/AddForumVideoScreen.swift`).
 *
 * iOS shows the bold "Choose a replay" heading, **the cover image
 * again** in a 150 dp frame with a 25 dp corner radius, and — when a
 * video has been picked — its poster frame with a 100 dp
 * `play.circle.fill` overlay that plays the video when tapped. A "Skip"
 * label sits bottom-leading and the circular next arrow
 * bottom-trailing; the arrow stays disabled until a video is chosen,
 * while Skip advances without one.
 */
@Composable
fun CreateForumVideoStep(
    imageUri: Uri?,
    videoUri: Uri?,
    videoThumbnail: Bitmap?,
    onVideoPicked: (Uri) -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    onPlayVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSourceDialog by remember { mutableStateOf(false) }

    val pickVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onVideoPicked(uri)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.space12),
    ) {
        Spacer(modifier = Modifier.height(Spacing.space8))
        CreateForumSectionHeading(
            text = stringResource(R.string.create_forum_choose_video)
        )

        Spacer(modifier = Modifier.height(Spacing.space12))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { showSourceDialog = true },
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
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.space12))
        VideoThumbnailPreview(
            videoUri = videoUri,
            videoThumbnail = videoThumbnail,
            onPlay = onPlayVideo,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        )

        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CreateForumSkipButton(onClick = onSkip)
            Spacer(modifier = Modifier.weight(1f))
            CreateForumNextButton(
                enabled = videoUri != null,
                onClick = onNext,
            )
        }
    }

    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text(text = stringResource(R.string.create_forum_select_a_video)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showSourceDialog = false
                            pickVideoLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.VideoOnly
                                )
                            )
                        },
                    ) {
                        Text(text = stringResource(R.string.create_forum_photo_library))
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
 * The video poster frame with a play badge.
 *
 * iOS renders the thumbnail with `.scaledToFit()` inside a frame with
 * a 25 dp corner radius and overlays a 100 dp `play.circle.fill`
 * (`AddForumVideoScreen.swift:113-134`). Tapping the frame plays the
 * video.
 */
@Composable
private fun VideoThumbnailPreview(
    videoUri: Uri?,
    videoThumbnail: Bitmap?,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (videoUri == null) return

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(25.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onPlay),
        contentAlignment = Alignment.Center,
    ) {
        VideoThumbnailImage(
            videoUri = videoUri,
            videoThumbnail = videoThumbnail,
            modifier = Modifier.fillMaxSize(),
        )
        Icon(
            imageVector = Icons.Filled.PlayCircle,
            contentDescription = stringResource(R.string.create_forum_cd_play_video),
            tint = Color.White,
            modifier = Modifier.size(100.dp),
        )
    }
}

/**
 * The poster frame for the picked video.
 *
 * The thumbnail is extracted by the ViewModel (which mirrors iOS's
 * `AVAssetImageGenerator` call) and handed down as a `Bitmap`. When
 * the frame could not be decoded — iOS renders the raw video in that
 * case — the video itself is shown in a `VideoView`, which plays the
 * first frame.
 */
@Composable
private fun VideoThumbnailImage(
    videoUri: Uri,
    videoThumbnail: Bitmap?,
    modifier: Modifier = Modifier,
) {
    if (videoThumbnail != null) {
        Image(
            bitmap = videoThumbnail.asImageBitmap(),
            contentDescription = stringResource(R.string.create_forum_cd_video),
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    } else {
        AndroidView(
            factory = { context ->
                android.widget.VideoView(context).apply {
                    setVideoURI(videoUri)
                }
            },
            modifier = modifier,
        )
    }
}
