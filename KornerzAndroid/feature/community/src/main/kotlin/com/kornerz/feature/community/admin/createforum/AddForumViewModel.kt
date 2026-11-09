package com.kornerz.feature.community.admin.createforum

import android.app.Application
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.kornerz.core.model.Loop
import com.kornerz.core.network.loops.LoopFirestoreApi
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import com.kornerz.feature.community.data.CommunitiesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import javax.inject.Inject

private const val MAX_TITLE_LENGTH = 100
private const val MAX_DESCRIPTION_LENGTH = 5000
private const val MAX_VIDEO_SIZE_MB = 500.0
private const val THUMBNAIL_MAX_DIMENSION = 200

/**
 * The five steps of the create-forum (create loop) wizard.
 *
 * Mirrors the iOS push chain in `kornerz/Kornerz/HomeVC/AddForum/`:
 *
 * ```
 * AddForumTitleScreen
 *   -> AddForumDescriptionScreen
 *     -> AddForumCoverImageScreen
 *       -> AddForumVideoScreen
 *         -> AddForumPublishScreen
 * ```
 *
 * The step lives in [AddForumUiState] rather than in a local `var` so
 * that the transition is a ViewModel event (`docs/architecture.md` §7
 * rule 1: no business logic in Composables), matching the canonical
 * signup wizard in `feature/auth/.../signup/SignupScreen.kt`.
 */
enum class CreateForumStep {
    Title,
    Description,
    CoverImage,
    Video,
    Publish,
}

/**
 * UI state for the create-forum (create loop) wizard. Mirrors iOS
 * `AddForumViewModel` published fields (`title`, `description`,
 * `selectedImage`, `selectedVideo`, `videoThumbnail`, `isUploading`,
 * `uploadProgress`, `uploadStep`, `uploadStepProgress`).
 */
data class AddForumUiState(
    val step: CreateForumStep = CreateForumStep.Title,
    val title: String = "",
    val description: String = "",
    val descriptionHtml: String? = null,
    val imageUri: Uri? = null,
    val videoUri: Uri? = null,
    val videoThumbnail: Bitmap? = null,
    val communityName: String = "",
    val publisherNickname: String = "",
    val publisherAvatarUrl: String? = null,
    val isUploading: Boolean = false,
    val uploadProgress: Double = 0.0,
    val uploadStep: String = "",
    val uploadStepProgress: Double = 0.0,
    val error: String? = null,
    val forumCreated: Boolean = false,
) {
    val titleOverLimit: Boolean get() = title.length > MAX_TITLE_LENGTH
    val descriptionOverLimit: Boolean get() = description.length > MAX_DESCRIPTION_LENGTH
}

sealed class AddForumEvent {
    data class TitleChanged(val text: String) : AddForumEvent()
    data class DescriptionChanged(val text: String) : AddForumEvent()
    data class DescriptionHtmlChanged(val html: String) : AddForumEvent()
    data class ImageSelected(val uri: Uri) : AddForumEvent()
    data class VideoSelected(val uri: Uri) : AddForumEvent()
    object VideoRemoved : AddForumEvent()
    object Publish : AddForumEvent()

    /** Advances to the next wizard step. */
    object Next : AddForumEvent()

    /**
     * Returns to the previous wizard step.
     *
     * iOS clears the description when the user backs out of the
     * description step (`AddForumDescriptionScreen.swift:120`), so the
     * Android back transition does the same via [descriptionCleared].
     */
    object Back : AddForumEvent()
}

sealed class AddForumNavEvent {
    /** The loop was published successfully — the wizard closes. */
    object ForumCreated : AddForumNavEvent()

    /** The wizard was abandoned before publishing — the wizard closes. */
    object CloseWizard : AddForumNavEvent()

    data class ShowError(val message: String) : AddForumNavEvent()
}

/**
 * ViewModel for the create-forum wizard. Mirrors iOS
 * `AddForumViewModel` (`kornerz/Kornerz/HomeVC/AddForum/AddForumViewModel.swift`):
 * the user enters a title (+description), picks a compulsory cover image
 * and an optional video, then `publish()` uploads media to Firebase
 * Storage and writes a `Loop` document via [LoopFirestoreApi.createLoop].
 *
 * `publisherId` is the signed-in user's `userCode` (mirrors iOS
 * `AppHelper.shared.loggedInUser?.userCode`), never the Firebase Auth uid.
 */
@HiltViewModel
class AddForumViewModel @Inject constructor(
    application: Application,
    private val sessionRepository: SessionRepository,
    private val loopFirestoreApi: LoopFirestoreApi,
    private val communitiesRepository: CommunitiesRepository,
    private val storage: FirebaseStorage,
) : AndroidViewModel(application) {

    private val contentResolver: ContentResolver = application.contentResolver

    private val _state = MutableStateFlow(AddForumUiState())
    val state: StateFlow<AddForumUiState> = _state.asStateFlow()

    private val _navEvents = Channel<AddForumNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<AddForumNavEvent> = _navEvents.receiveAsFlow()

    private var communityId: String? = null

    /**
     * Binds the wizard to its community and resolves the copy the
     * publish preview needs: the community's name (iOS reads it from the
     * `Community` model it was constructed with — `AddForumViewModel.init`
     * — and renders it in blue under the title) and the signed-in user's
     * nickname (iOS reads `AppHelper.shared.user?.nickName`).
     */
    fun setCommunity(communityId: String) {
        this.communityId = communityId
        viewModelScope.launch {
            val publisher = sessionRepository.data
                .filterNot { it is SessionState.Loading }
                .map { state -> (state as? SessionState.Authenticated)?.user }
                .first()
            val communityName = resolveCommunityName(communityId)
            _state.update {
                it.copy(
                    publisherNickname = publisher?.nickname.orEmpty(),
                    publisherAvatarUrl = publisher?.thumbnailURL,
                    communityName = communityName,
                )
            }
        }
    }

    /**
     * The community's display name for the publish preview.
     *
     * iOS already holds the whole `Community` model
     * (`AddForumViewModel.init`), so the name is right there. Android
     * only carries the id across the route, so the name is read back
     * from Firestore.
     *
     * The lookup tries the `communityId` field first and the 6-char
     * `idCommunity` code second, so it resolves for both the iOS-written
     * schema and the Android-written one. A community that cannot be
     * resolved leaves the preview's name empty rather than failing the
     * publish — the id itself is what the loop is written under.
     */
    private suspend fun resolveCommunityName(communityId: String): String {
        if (communityId.isBlank()) return ""
        return communitiesRepository.fetchByCode(communityId)?.name.orEmpty()
    }

    fun onEvent(event: AddForumEvent) {
        when (event) {
            is AddForumEvent.TitleChanged -> {
                val text = event.text.take(MAX_TITLE_LENGTH)
                _state.update { it.copy(title = text, error = null) }
            }
            is AddForumEvent.DescriptionChanged -> {
                val text = event.text.take(MAX_DESCRIPTION_LENGTH)
                _state.update { it.copy(description = text, error = null) }
            }
            is AddForumEvent.DescriptionHtmlChanged -> {
                _state.update { it.copy(descriptionHtml = event.html) }
            }
            is AddForumEvent.ImageSelected -> _state.update { it.copy(imageUri = event.uri, error = null) }
            is AddForumEvent.VideoRemoved -> _state.update {
                it.copy(videoUri = null, videoThumbnail = null, error = null)
            }
            is AddForumEvent.VideoSelected -> onVideoSelected(event.uri)
            AddForumEvent.Publish -> publish()
            AddForumEvent.Next -> goNext()
            AddForumEvent.Back -> goBack()
        }
    }

    /**
     * Advances one step forward, mirroring the iOS `NavigationLink`
     * chain. The guard conditions are the same ones the iOS screens
     * enforce by disabling their next button:
     *
     * - title step: the title must be non-empty
     * - description step: the description must be non-empty
     * - cover-image step: an image must be selected
     * - video step: a video is optional, so it always advances
     */
    private fun goNext() {
        val current = _state.value
        val next = when (current.step) {
            CreateForumStep.Title ->
                if (current.title.isBlank()) return else CreateForumStep.Description
            CreateForumStep.Description ->
                if (current.description.isBlank()) return else CreateForumStep.CoverImage
            CreateForumStep.CoverImage ->
                if (current.imageUri == null) return else CreateForumStep.Video
            CreateForumStep.Video -> CreateForumStep.Publish
            CreateForumStep.Publish -> return
        }
        _state.update { it.copy(step = next, error = null) }
    }

    /**
     * Returns one step back. From the title step the wizard is closed
     * instead — iOS dismisses the pushed `UIHostingController` from the
     * leading toolbar button on the first screen.
     *
     * Backing out of the description step clears the description first,
     * mirroring iOS (`AddForumDescriptionScreen.swift:120` — the
     * leading back button assigns `NSAttributedString()` before it
     * dismisses).
     */
    private fun goBack() {
        val current = _state.value
        val previous = when (current.step) {
            CreateForumStep.Title -> {
                _navEvents.trySend(AddForumNavEvent.CloseWizard)
                return
            }
            CreateForumStep.Description -> {
                _state.update { it.copy(description = "", descriptionHtml = null) }
                CreateForumStep.Title
            }
            CreateForumStep.CoverImage -> CreateForumStep.Description
            CreateForumStep.Video -> CreateForumStep.CoverImage
            CreateForumStep.Publish -> CreateForumStep.Video
        }
        _state.update { it.copy(step = previous, error = null) }
    }

    /**
     * Handles a freshly picked video: enforces the iOS 500 MB limit
     * (`AddForumViewModel.videoSelected`), then extracts a poster frame
     * so the wizard can show the same thumbnail preview iOS renders
     * (`AVAssetImageGenerator` at the 1-second mark).
     */
    private fun onVideoSelected(uri: Uri) {
        viewModelScope.launch {
            val sizeInMb = videoSizeInMb(uri)
            if (sizeInMb != null && sizeInMb > MAX_VIDEO_SIZE_MB) {
                _state.update {
                    it.copy(
                        videoUri = null,
                        videoThumbnail = null,
                        error = VIDEO_TOO_LARGE_MESSAGE,
                    )
                }
                return@launch
            }
            val thumbnail = extractVideoThumbnail(uri)
            _state.update {
                it.copy(videoUri = uri, videoThumbnail = thumbnail, error = null)
            }
        }
    }

    /**
     * The picked video's size in megabytes, or `null` when the size
     * cannot be resolved.
     */
    private fun videoSizeInMb(uri: Uri): Double? =
        runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (!cursor.moveToFirst() || sizeIndex < 0) return@use null
                val bytes = cursor.getLong(sizeIndex)
                if (bytes <= 0) null else bytes / (1024.0 * 1024.0)
            }
        }.getOrNull()

    /**
     * Extracts a poster frame from the video, mirroring iOS's
     * `generateVideoThumbnail` (which seeks to the 1-second mark).
     * Returns `null` when the frame cannot be decoded — the wizard then
     * falls back to rendering the video itself in a `VideoView`.
     */
    private suspend fun extractVideoThumbnail(uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val descriptor = contentResolver.openFileDescriptor(uri, "r")
                    ?: return@runCatching null
                val retriever = MediaMetadataRetriever()
                try {
                    descriptor.use {
                        retriever.setDataSource(it.fileDescriptor)
                    }
                    retriever.getFrameAtTime(THUMBNAIL_TIMESTAMP_US)
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }

    /**
     * Publishes the forum. 1:1 port of iOS
     * `AddForumViewModel.publishForum()` (`AddForumViewModel.swift:80-99`),
     * which splits into three paths depending on whether a video was
     * attached:
     *
     * ```
     * publishForum()
     *   -> uploadVideoThenImage(videoURL:image:)   (video attached)
     *   -> uploadImageOnly(image:)                 (no video)
     *      -> uploadImage(image:videoHash:startProgress:)
     *         -> createLoopWithHashes(...)
     * ```
     *
     * The Android port keeps the same ordering and the same progress
     * bands:
     *
     * | band | owner |
     * |------|-------|
     * | 0.00 – 0.40 | optional video upload |
     * | 0.40 – 0.80 | cover-image upload (the thumbnail is uploaded in parallel and, like iOS, reports no progress of its own) |
     * | 0.80 – 1.00 | Firestore loop creation (simulated, as on iOS) |
     *
     * Every failure surfaces through [AddForumNavEvent.ShowError] — iOS
     * calls `showErrorMessage(error:)` (`AddForumViewModel.swift:285-290`)
     * which presents an alert controller. Without that event the Android
     * wizard failed silently: the user tapped "Publish" and nothing
     * visible happened.
     */
    private fun publish() {
        val s = _state.value
        if (s.title.isBlank()) {
            fail(ERROR_TITLE_REQUIRED)
            return
        }
        if (s.imageUri == null) {
            fail(ERROR_SELECT_IMAGE)
            return
        }
        if (communityId.isNullOrBlank()) {
            fail(ERROR_COMMUNITY_MISSING)
            return
        }

        _state.update {
            it.copy(
                isUploading = true,
                uploadProgress = 0.0,
                uploadStepProgress = 0.0,
                uploadStep = STEP_UPLOADING_IMAGE,
                error = null,
            )
        }

        viewModelScope.launch {
            val userCode = currentUserCode()
            if (userCode == null) {
                fail(ERROR_SIGN_IN)
                return@launch
            }

            val forumId = generateCode(20)
            var videoUrl: String? = null

            try {
                // Optional video first (mirrors iOS uploadVideoThenImage ordering)
                if (s.videoUri != null) {
                    _state.update {
                        it.copy(uploadStep = STEP_UPLOADING_VIDEO, uploadStepProgress = 0.0)
                    }
                    videoUrl = uploadFile(s.videoUri, "loops/$forumId/video/video.mp4") { progress ->
                        _state.update {
                            it.copy(
                                uploadProgress = progress * 0.4,
                                uploadStepProgress = progress,
                            )
                        }
                    }
                }

                // Compulsory image + thumbnail
                _state.update {
                    it.copy(uploadStep = STEP_UPLOADING_IMAGE, uploadStepProgress = 0.0)
                }
                val imageBytes = readBytes(s.imageUri)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bitmap == null) {
                    fail(ERROR_LOAD_IMAGE)
                    return@launch
                }
                val thumbnail = createThumbnail(bitmap)

                val imageStart = if (s.videoUri != null) 0.4 else 0.0
                val imageBytesJpeg = compressJpeg(bitmap, 0.8f)
                val thumbBytesJpeg = compressJpeg(thumbnail, 0.3f)

                // iOS uploads the cover image and its thumbnail **in
                // parallel** inside a `DispatchGroup`
                // (`AddForumViewModel.swift:160-199`) and waits for both
                // before creating the loop. Only the cover image reports
                // progress — the thumbnail upload is fired without a
                // progress handler — so the step bar tracks the cover
                // image alone and never jumps backwards.
                val imageRef = storage.reference.child("loops/$forumId/images/image.jpg")
                val thumbRef = storage.reference.child("loops/$forumId/images/thumb.jpg")

                val imageUpload = async {
                    uploadBytes(imageRef, imageBytesJpeg) { progress ->
                        _state.update {
                            it.copy(
                                uploadProgress = imageStart + (progress * 0.4),
                                uploadStepProgress = progress,
                            )
                        }
                    }
                }
                val thumbUpload = async {
                    uploadBytes(thumbRef, thumbBytesJpeg)
                }

                val imageUrl = imageUpload.await()
                val thumbUrl = thumbUpload.await()

                // Create the loop document (mirrors iOS createLoopWithHashes → repository.createLoop)
                _state.update {
                    it.copy(
                        uploadStep = STEP_CREATING_FORUM,
                        uploadProgress = 0.8,
                        uploadStepProgress = 0.0,
                    )
                }
                val createJob = async {
                    val loop = Loop(
                        id = forumId,
                        communityId = communityId,
                        publisherId = userCode,
                        active = true,
                        title = s.title,
                        description = s.description.takeIf { it.isNotBlank() },
                        descriptionHtml = s.descriptionHtml,
                        imageHash = null,
                        thumbnailHash = null,
                        videoHash = null,
                        firebaseImageUrl = imageUrl,
                        firebaseThumbnailUrl = thumbUrl,
                        firebaseVideoUrl = videoUrl,
                        creationDate = Clock.System.now(),
                    )
                    loopFirestoreApi.createLoop(loop)
                }

                // iOS animates the last 20% with a 0.1 s timer while the
                // Firestore write is in flight
                // (`AddForumViewModel.swift:242-258`) so the bar keeps
                // moving instead of freezing on 80%.
                val progressJob = launch {
                    while (isActive) {
                        delay(CREATE_PROGRESS_TICK_MS)
                        _state.update {
                            val next = (it.uploadStepProgress + CREATE_PROGRESS_TICK).coerceAtMost(1.0)
                            it.copy(
                                uploadStepProgress = next,
                                uploadProgress = 0.8 + (next * 0.2),
                            )
                        }
                    }
                }

                val createResult = createJob.await()
                progressJob.cancel()

                createResult.onFailure { error ->
                    fail(error.message ?: ERROR_CREATE)
                    return@launch
                }

                _state.update {
                    it.copy(
                        isUploading = false,
                        uploadProgress = 1.0,
                        uploadStepProgress = 1.0,
                        forumCreated = true,
                    )
                }
                _navEvents.trySend(AddForumNavEvent.ForumCreated)
            } catch (e: Exception) {
                fail(e.message ?: ERROR_CREATE)
            }
        }
    }

    /**
     * Aborts the publish and surfaces [message] to the user.
     *
     * Mirrors iOS `showErrorMessage(error:)`
     * (`AddForumViewModel.swift:285-290`): the upload flag is cleared so
     * the progress card disappears, the message is kept on the state for
     * the screen to render, and a [AddForumNavEvent.ShowError] event is
     * emitted so the screen can present an alert.
     */
    private fun fail(message: String) {
        _state.update { it.copy(isUploading = false, error = message) }
        _navEvents.trySend(AddForumNavEvent.ShowError(message))
    }

    /** Signed-in user's identity as stored in loop `publisherId` (the user code). */
    private suspend fun currentUserCode(): String? =
        sessionRepository.data
            .filterNot { it is SessionState.Loading }
            .map { state ->
                val user = (state as? SessionState.Authenticated)?.user ?: return@map null
                user.userCode?.takeIf { it.isNotBlank() } ?: user.id.takeIf { it.isNotBlank() }
            }
            .first()

    private suspend fun readBytes(uri: Uri): ByteArray =
        contentResolver.openInputStream(uri).use { input ->
            input?.readBytes() ?: byteArrayOf()
        }

    private fun createThumbnail(bitmap: Bitmap, maxDimension: Int = THUMBNAIL_MAX_DIMENSION): Bitmap {
        val scale = (maxDimension.toFloat() / bitmap.width).coerceAtMost(1f)
        val hScale = maxDimension.toFloat() / bitmap.height
        val finalScale = minOf(scale, hScale)
        val newW = (bitmap.width * finalScale).toInt().coerceAtLeast(1)
        val newH = (bitmap.height * finalScale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun compressJpeg(bitmap: Bitmap, quality: Float): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, (quality * 100).toInt(), out)
        return out.toByteArray()
    }

    /**
     * Uploads [bytes] to [ref] and returns its download URL.
     *
     * [onProgress] is optional: iOS fires the thumbnail upload without a
     * progress handler (`AddForumViewModel.swift:181-189`) so only the
     * cover image drives the progress bar.
     */
    private suspend fun uploadBytes(
        ref: StorageReference,
        bytes: ByteArray,
        onProgress: ((Double) -> Unit)? = null,
    ): String {
        val task = ref.putBytes(bytes)
        if (onProgress != null) {
            task.addOnProgressListener { snapshot ->
                val total = snapshot.totalByteCount
                if (total > 0) {
                    val progress = snapshot.bytesTransferred.toDouble() / total
                    viewModelScope.launch {
                        withContext(Dispatchers.Main) {
                            onProgress(progress)
                        }
                    }
                }
            }
        }
        task.await()
        return ref.downloadUrl.await().toString()
    }

    private suspend fun uploadFile(uri: Uri, path: String, onProgress: (Double) -> Unit): String {
        val ref = storage.reference.child(path)
        val task = ref.putFile(uri)
        task.addOnProgressListener { snapshot ->
            val total = snapshot.totalByteCount
            if (total > 0) {
                val progress = snapshot.bytesTransferred.toDouble() / total
                viewModelScope.launch {
                    withContext(Dispatchers.Main) {
                        onProgress(progress)
                    }
                }
            }
        }
        task.await()
        return ref.downloadUrl.await().toString()
    }

    private fun generateCode(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }
}

// iOS `AddForumViewModel.UploadStep` raw values.
private const val STEP_UPLOADING_IMAGE = "Uploading image..."
private const val STEP_UPLOADING_VIDEO = "Uploading video..."
private const val STEP_CREATING_FORUM = "Creating forum..."

// iOS `AddForumViewModel.showErrorMessage` copy.
private const val ERROR_SELECT_IMAGE = "Please select an image"
private const val ERROR_SIGN_IN = "Please sign in to create a forum"
private const val ERROR_LOAD_IMAGE = "Failed to load image"
private const val ERROR_CREATE = "Failed to create forum"
private const val ERROR_COMMUNITY_MISSING = "Community context missing"
private const val ERROR_TITLE_REQUIRED = "Please enter a title"
private const val VIDEO_TOO_LARGE_MESSAGE =
    "Video file size is too big. Maximum allowed size is 500MB."

/** The 1-second poster-frame mark, mirroring iOS's `CMTime(seconds: 1)`. */
private const val THUMBNAIL_TIMESTAMP_US = 1_000_000L

/**
 * The interval between the simulated "Creating forum..." progress ticks.
 * iOS drives the same animation with a `Timer.scheduledTimer` at a
 * 0.1-second interval (`AddForumViewModel.swift:242-258`).
 */
private const val CREATE_PROGRESS_TICK_MS = 100L

/**
 * The step added to the "Creating forum..." progress on each tick.
 * iOS adds 0.1 per tick, so the simulated band fills in one second.
 */
private const val CREATE_PROGRESS_TICK = 0.1
