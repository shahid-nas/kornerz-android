package com.kornerz.feature.community.admin.createforum

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kornerz.core.ui.theme.Satoshi

private const val TITLE_LABEL = "forum_title"
private const val DESCRIPTION_LABEL = "forum_description"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateForumScreen(
    communityId: String,
    onFinished: () -> Unit,
    viewModel: AddForumViewModel = hiltViewModel(),
) {
    LaunchedEffect(communityId) {
        viewModel.setCommunity(communityId)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf(CreateForumStep.Title) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Create forum",
                        fontFamily = Satoshi,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF353446),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onFinished() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF353446),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (step) {
                CreateForumStep.Title -> TitleStep(
                    title = state.title,
                    onTitleChange = { viewModel.onEvent(AddForumEvent.TitleChanged(it)) },
                    onNext = {
                        if (state.title.isNotBlank()) step = CreateForumStep.Description
                    },
                )
                CreateForumStep.Description -> DescriptionStep(
                    description = state.description,
                    onDescriptionChange = { viewModel.onEvent(AddForumEvent.DescriptionChanged(it)) },
                    onNext = {
                        if (state.description.isNotBlank()) step = CreateForumStep.Media
                    },
                    onBack = { step = CreateForumStep.Title },
                )
                CreateForumStep.Media -> MediaStep(
                    imageUri = state.imageUri,
                    videoUri = state.videoUri,
                    onImagePicked = { viewModel.setImage(it) },
                    onVideoPicked = { viewModel.setVideo(it) },
                    onRemoveVideo = { viewModel.setVideo(null) },
                    onNext = {
                        if (state.imageUri != null) step = CreateForumStep.Publish
                    },
                    onBack = { step = CreateForumStep.Description },
                )
                CreateForumStep.Publish -> PublishStep(
                    state = state,
                    onPublish = { viewModel.onEvent(AddForumEvent.Publish) },
                    onBack = { step = CreateForumStep.Media },
                    onFinished = onFinished,
                )
            }
        }
    }
}

private enum class CreateForumStep {
    Title, Description, Media, Publish
}

@Composable
private fun TitleStep(
    title: String,
    onTitleChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 24.dp, end = 20.dp),
    ) {
        Text(
            text = TITLE_LABEL,
            fontFamily = Satoshi,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = Color(0xFF353446),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text(text = TITLE_LABEL) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${title.length} of 100",
            fontFamily = Satoshi,
            fontSize = 12.sp,
            color = Color(0xFF9E9EAE),
            modifier = Modifier.align(alignment = Alignment.End),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = onNext,
                enabled = title.isNotBlank(),
                modifier = Modifier.width(100.dp),
            ) {
                Text(text = "Next", fontFamily = Satoshi, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DescriptionStep(
    description: String,
    onDescriptionChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 24.dp, end = 20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = DESCRIPTION_LABEL,
                fontFamily = Satoshi,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFF353446),
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) {
                Text(text = "Back", fontFamily = Satoshi, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text(text = DESCRIPTION_LABEL) },
            modifier = Modifier.fillMaxWidth().height(200.dp),
            singleLine = false,
            maxLines = 10,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${description.length} of 5000",
            fontFamily = Satoshi,
            fontSize = 12.sp,
            color = Color(0xFF9E9EAE),
            modifier = Modifier.align(alignment = Alignment.End),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = onNext,
                enabled = description.isNotBlank(),
                modifier = Modifier.width(100.dp),
            ) {
                Text(text = "Next", fontFamily = Satoshi, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MediaStep(
    imageUri: android.net.Uri?,
    videoUri: android.net.Uri?,
    onImagePicked: (android.net.Uri) -> Unit,
    onVideoPicked: (android.net.Uri) -> Unit,
    onRemoveVideo: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val pickImageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val pickVideoOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImagePicked(uri)
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onVideoPicked(uri)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 24.dp, end = 20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Choose cover image",
                fontFamily = Satoshi,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFF353446),
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) {
                Text(text = "Back", fontFamily = Satoshi, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color(0xFFF5F5F5))
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFFCEC8FA), RoundedCornerShape(12.dp))
                .clickable(onClick = { imageLauncher.launch(pickImageOnly) }),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Cover image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = Color(0xFF5D47ED),
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap to choose cover image",
                        fontFamily = Satoshi,
                        fontSize = 14.sp,
                        color = Color(0xFF75748B),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = "Add video (optional)",
                fontFamily = Satoshi,
                fontSize = 16.sp,
                color = Color(0xFF353446),
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { videoLauncher.launch(pickVideoOnly) }) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Pick video",
                    tint = Color(0xFF5D47ED),
                )
            }
        }
        if (videoUri != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(Color(0xFFF5F5F5))
                    .clip(RoundedCornerShape(8.dp)),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Video selected",
                    tint = Color(0xFF5D47ED),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Video attached",
                    fontFamily = Satoshi,
                    fontSize = 14.sp,
                    color = Color(0xFF353446),
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onRemoveVideo) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove video",
                        tint = Color(0xFFC44E55),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = onNext,
                enabled = imageUri != null,
                modifier = Modifier.width(100.dp),
            ) {
                Text(text = "Next", fontFamily = Satoshi, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PublishStep(
    state: AddForumUiState,
    onPublish: () -> Unit,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 24.dp, end = 20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Publish",
                fontFamily = Satoshi,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFF353446),
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) {
                Text(text = "Back", fontFamily = Satoshi, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = state.title,
            fontFamily = Satoshi,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color(0xFF353446),
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (state.description.isNotBlank()) {
            Text(
                text = state.description,
                fontFamily = Satoshi,
                fontSize = 14.sp,
                color = Color(0xFF353446),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (state.imageUri != null) {
            AsyncImage(
                model = state.imageUri,
                contentDescription = "Cover image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (state.videoUri != null) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Video",
                    tint = Color(0xFF5D47ED),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Video attached", fontFamily = Satoshi, fontSize = 14.sp, color = Color(0xFF353446))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (state.error != null) {
            Text(
                text = state.error!!,
                fontFamily = Satoshi,
                fontSize = 14.sp,
                color = Color(0xFFC44E55),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (state.isUploading) {
            LinearProgressIndicator(
                progress = state.uploadProgress.toFloat(),
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.uploadStep.takeIf { it.isNotBlank() } ?: "Publishing...",
                fontFamily = Satoshi,
                fontSize = 13.sp,
                color = Color(0xFF75748B),
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = {
                    if (state.forumCreated) onFinished() else onPublish()
                },
                enabled = !state.isUploading || state.forumCreated,
                modifier = Modifier.width(100.dp),
            ) {
                if (state.forumCreated) {
                    Text(text = "Done", fontFamily = Satoshi, fontWeight = FontWeight.Bold)
                } else {
                    Text(text = "Publish", fontFamily = Satoshi, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (state.forumCreated) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Forum created",
                fontFamily = Satoshi,
                fontSize = 14.sp,
                color = Color(0xFF2E7D32),
            )
        }
    }
}
