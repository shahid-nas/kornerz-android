package com.kornerz.feature.community.admin.createforum

import android.app.Application
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import com.kornerz.core.model.Loop
import com.kornerz.core.network.loops.LoopFirestoreApi
import com.kornerz.core.storage.session.SessionRepository
import com.kornerz.core.storage.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
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
 * UI state for the create-forum (create loop) wizard. Mirrors iOS
 * `AddForumViewModel` published fields (`title`, `description`,
 * `selectedImage`, `selectedVideo`, `isUploading`, `uploadProgress`,
 * `uploadStep`, `uploadStepProgress`).
 */
data class AddForumUiState(
    val title: String = "",
    val description: String = "",
    val imageUri: Uri? = null,
    val videoUri: Uri? = null,
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
    object Refresh : AddForumEvent()
    data class TitleChanged(val text: String) : AddForumEvent()
    data class DescriptionChanged(val text: String) : AddForumEvent()
    data class ImageSelected(val uri: Uri) : AddForumEvent()
    data class VideoSelected(val uri: Uri) : AddForumEvent()
    object VideoRemoved : AddForumEvent()
    object Publish : AddForumEvent()
}

sealed class AddForumNavEvent {
    object ForumCreated : AddForumNavEvent()
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
    private val storage: FirebaseStorage,
) : AndroidViewModel(application) {

    private val contentResolver: ContentResolver = application.contentResolver

    private val _state = MutableStateFlow(AddForumUiState())
    val state: StateFlow<AddForumUiState> = _state.asStateFlow()

    private val _navEvents = Channel<AddForumNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<AddForumNavEvent> = _navEvents.receiveAsFlow()

    private var communityId: String? = null

    fun setCommunity(communityId: String) {
        this.communityId = communityId
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
            is AddForumEvent.ImageSelected -> _state.update { it.copy(imageUri = event.uri, error = null) }
            is AddForumEvent.VideoSelected -> _state.update { it.copy(videoUri = event.uri, error = null) }
            is AddForumEvent.VideoRemoved -> _state.update { it.copy(videoUri = null, error = null) }
            AddForumEvent.Publish -> publish()
            else -> { }
        }
    }

    fun setImage(uri: Uri?) {
        _state.update { it.copy(imageUri = uri, error = null) }
    }

    fun setVideo(uri: Uri?) {
        _state.update { it.copy(videoUri = uri, error = null) }
    }

    private fun publish() {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.update { it.copy(error = "Please enter a title") }
            return
        }
        if (s.imageUri == null) {
            _state.update { it.copy(error = "Please select an image") }
            return
        }
        if (communityId.isNullOrBlank()) {
            _state.update { it.copy(error = "Community context missing") }
            return
        }

        _state.update {
            it.copy(
                isUploading = true,
                uploadProgress = 0.0,
                uploadStepProgress = 0.0,
                uploadStep = "Uploading image...",
                error = null,
            )
        }

        viewModelScope.launch {
            val userCode = currentUserCode()
                ?: run {
                    _state.update { it.copy(isUploading = false, error = "Please sign in to create a forum") }
                    return@launch
                }

            val forumId = generateCode(20)
            var videoUrl: String? = null

            try {
                // Optional video first (mirrors iOS uploadVideoThenImage ordering)
                if (s.videoUri != null) {
                    _state.update { it.copy(uploadStep = "Uploading video...", uploadStepProgress = 0.0) }
                    videoUrl = uploadFile(s.videoUri!!, "loops/$forumId/video/video.mp4") { progress ->
                        _state.update {
                            it.copy(
                                uploadProgress = progress * 0.4,
                                uploadStepProgress = progress,
                            )
                        }
                    }
                }

                // Compulsory image + thumbnail
                _state.update { it.copy(uploadStep = "Uploading image...", uploadStepProgress = 0.0) }
                val imageBytes = readBytes(s.imageUri!!)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    ?: run {
                        _state.update { it.copy(isUploading = false, error = "Failed to load image") }
                        return@launch
                    }
                val thumbnail = createThumbnail(bitmap)

                val imageStart = if (s.videoUri != null) 0.4 else 0.0
                val imageBytesJpeg = compressJpeg(bitmap, 0.8f)
                val thumbBytesJpeg = compressJpeg(thumbnail, 0.3f)

                val imageUrl = uploadBytes(
                    storage.reference.child("loops/$forumId/images/image.jpg"),
                    imageBytesJpeg,
                ) { progress ->
                    _state.update {
                        it.copy(
                            uploadProgress = imageStart + (progress * 0.4),
                            uploadStepProgress = progress,
                        )
                    }
                }
                val thumbUrl = uploadBytes(
                    storage.reference.child("loops/$forumId/images/thumb.jpg"),
                    thumbBytesJpeg,
                ) { progress ->
                    _state.update {
                        it.copy(
                            uploadProgress = imageStart + 0.4 + (progress * 0.4),
                            uploadStepProgress = progress,
                        )
                    }
                }

                // Create the loop document (mirrors iOS createLoopWithHashes → repository.createLoop)
                _state.update { it.copy(uploadStep = "Creating forum...", uploadProgress = 0.8, uploadStepProgress = 0.0) }
                val loop = Loop(
                    id = forumId,
                    communityId = communityId,
                    publisherId = userCode,
                    active = true,
                    title = s.title,
                    description = s.description.takeIf { it.isNotBlank() },
                    descriptionHtml = null,
                    imageHash = null,
                    thumbnailHash = null,
                    videoHash = null,
                    firebaseImageUrl = imageUrl,
                    firebaseThumbnailUrl = thumbUrl,
                    firebaseVideoUrl = videoUrl,
                    creationDate = Clock.System.now(),
                )
                loopFirestoreApi.createLoop(loop).onFailure { error ->
                    _state.update { it.copy(isUploading = false, error = error.message ?: "Failed to create forum") }
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
                _state.update { it.copy(isUploading = false, error = e.message ?: "Failed to create forum") }
            }
        }
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

    private suspend fun uploadBytes(ref: StorageReference, bytes: ByteArray, onProgress: (Double) -> Unit): String {
        val task = ref.putBytes(bytes)
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

    companion object {
        private const val TAG = "AddForumViewModel"
    }
}
