package com.kornerz.feature.community.admin.createforum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kornerz.core.ui.components.KornerzCapsuleButton
import com.kornerz.core.ui.components.KornerzUploadProgressView
import com.kornerz.core.ui.theme.Spacing
import com.kornerz.feature.community.R

/**
 * The create-forum (create loop) wizard.
 *
 * 1:1 port of the iOS push chain in
 * `kornerz/Kornerz/HomeVC/AddForum/`:
 *
 * ```
 * AddForumTitleScreen          (step 1)
 *   -> AddForumDescriptionScreen   (step 2)
 *     -> AddForumCoverImageScreen  (step 3)
 *       -> AddForumVideoScreen     (step 4)
 *         -> AddForumPublishScreen (step 5)
 * ```
 *
 * iOS pushes a separate view controller per step, so each screen owns
 * its own top bar. Android keeps the wizard on a single destination
 * and switches the content by [CreateForumStep], which lives in the
 * ViewModel's state (`AddForumUiState.step`) so the transitions are
 * events rather than local composable state — the same shape as the
 * canonical signup wizard in `feature/auth/.../signup/SignupScreen.kt`.
 *
 * The top bar's back button steps back through the wizard and the
 * close button abandons it, matching iOS's leading `Image("back")` and
 * trailing `Image("cross")` toolbar items.
 */
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
    var showVideoPlayer by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event ->
            when (event) {
                AddForumNavEvent.ForumCreated -> onFinished()
                AddForumNavEvent.CloseWizard -> onFinished()
                is AddForumNavEvent.ShowError -> errorMessage = event.message
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CreateForumTopBar(
                title = stringResource(
                    if (state.step == CreateForumStep.Publish) {
                        R.string.create_forum_publish
                    } else {
                        R.string.create_forum_title
                    }
                ),
                onBack = { viewModel.onEvent(AddForumEvent.Back) },
                onClose = onFinished,
            )

            when (state.step) {
                CreateForumStep.Title -> CreateForumTitleStep(
                    title = state.title,
                    onTitleChange = { viewModel.onEvent(AddForumEvent.TitleChanged(it)) },
                    onNext = { viewModel.onEvent(AddForumEvent.Next) },
                )

                CreateForumStep.Description -> CreateForumDescriptionStep(
                    description = state.description,
                    onDescriptionChange = {
                        viewModel.onEvent(AddForumEvent.DescriptionChanged(it))
                    },
                    onDescriptionHtmlChange = {
                        viewModel.onEvent(AddForumEvent.DescriptionHtmlChanged(it))
                    },
                    onNext = { viewModel.onEvent(AddForumEvent.Next) },
                )

                CreateForumStep.CoverImage -> CreateForumCoverImageStep(
                    imageUri = state.imageUri,
                    onImagePicked = { viewModel.onEvent(AddForumEvent.ImageSelected(it)) },
                    onNext = { viewModel.onEvent(AddForumEvent.Next) },
                )

                CreateForumStep.Video -> CreateForumVideoStep(
                    videoUri = state.videoUri,
                    videoThumbnail = state.videoThumbnail,
                    onVideoPicked = { viewModel.onEvent(AddForumEvent.VideoSelected(it)) },
                    onSkip = { viewModel.onEvent(AddForumEvent.Next) },
                    onNext = { viewModel.onEvent(AddForumEvent.Next) },
                    onPlayVideo = { showVideoPlayer = true },
                )

                CreateForumStep.Publish -> {
                    CreateForumPublishStep(
                        title = state.title,
                        description = state.description,
                        descriptionHtml = state.descriptionHtml,
                        imageUri = state.imageUri,
                        hasVideo = state.videoUri != null,
                        publisherNickname = state.publisherNickname,
                        publisherAvatarUrl = state.publisherAvatarUrl,
                        communityName = state.communityName,
                        onPlayVideo = { showVideoPlayer = true },
                        modifier = Modifier.weight(1f),
                    )
                    CreateForumPublishBar(
                        enabled = !state.isUploading,
                        onPublish = { viewModel.onEvent(AddForumEvent.Publish) },
                    )
                }
            }
        }

        // iOS dims the whole screen with a 30% black scrim while the
        // upload runs and floats the progress card over it
        // (`AddForumPublishScreen.swift:47-61`).
        if (state.isUploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                KornerzUploadProgressView(
                    isVisible = true,
                    progress = state.uploadProgress,
                    step = state.uploadStep,
                    stepProgress = state.uploadStepProgress,
                    modifier = Modifier.padding(Spacing.space16),
                )
            }
        }

        val videoUri = state.videoUri
        if (showVideoPlayer && videoUri != null) {
            CreateForumVideoPlayer(
                videoUri = videoUri,
                onDismiss = { showVideoPlayer = false },
            )
        }
    }

    // iOS presents every publish failure through
    // `showAlertController(message:alertTitle:okayButtonTitle:)`
    // (`AddForumViewModel.swift:285-290` → `AppHelper`), which shows a
    // titled alert with a single "OK" button. The Android port mirrors
    // it with a Material alert dialog driven by
    // [AddForumNavEvent.ShowError].
    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text(text = stringResource(R.string.create_forum_error_title)) },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text(text = stringResource(R.string.create_forum_ok))
                }
            },
        )
    }
}

/**
 * The publish action.
 *
 * iOS pins a full-width capsule button to the bottom of the screen via
 * `.safeAreaInset(edge: .bottom)` and hides it while the upload runs
 * (`AddForumPublishScreen.swift:40-45` and `:192-206`).
 *
 * The button is the shared [KornerzCapsuleButton] — the Android twin of
 * iOS's `.buttonStyle(.borderedProminent)` + `.buttonBorderShape(.capsule)`
 * pair — tinted with the brand primary colour, which is what
 * `.tint(Color(AppColor.C5D47ED))` resolves to on iOS.
 */
@Composable
private fun CreateForumPublishBar(
    enabled: Boolean,
    onPublish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.space12),
    ) {
        KornerzCapsuleButton(
            text = stringResource(R.string.create_forum_publish),
            onClick = onPublish,
            enabled = enabled,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
        )
    }
}
