package com.example.music_app.ui.yourupload

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.UploadMusicRequest
import com.example.music_app.domain.usecase.UploadMusicUseCase
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

/** Owns upload state so the Fragment does not call Firebase directly. */
class UploadMusicViewModel(
    private val uploadMusicUseCase: UploadMusicUseCase = UploadMusicUseCase()
) : ViewModel() {

    // Screen state
    private val _uploadState = MutableLiveData<UploadMusicUiState>(UploadMusicUiState.Idle)
    val uploadState: LiveData<UploadMusicUiState> = _uploadState

    // Public screen actions
    fun uploadMusic(
        audioUri: Uri?,
        coverUri: Uri?,
        audioExtension: String,
        coverExtension: String?,
        title: String,
        artist: String,
        genre: String,
        tags: List<String>,
        unknownArtist: String
    ) {
        when {
            audioUri == null -> {
                _uploadState.value = UploadMusicUiState.Error(
                    messageResId = R.string.please_select_audio_file
                )
                return
            }

            title.isBlank() -> {
                _uploadState.value = UploadMusicUiState.Error(
                    messageResId = R.string.song_title_required
                )
                return
            }
        }

        viewModelScope.launch {
            // Prevent duplicate taps while Storage and Firestore are being updated.
            _uploadState.value = UploadMusicUiState.Loading

            try {
                uploadMusicUseCase(
                    UploadMusicRequest(
                        audioUri = audioUri,
                        coverUri = coverUri,
                        audioExtension = audioExtension,
                        coverExtension = coverExtension,
                        title = title,
                        artist = artist,
                        genre = genre,
                        tags = tags,
                        unknownArtist = unknownArtist
                    )
                )
                _uploadState.value = UploadMusicUiState.Success
            } catch (error: AppException) {
                _uploadState.value = UploadMusicUiState.Error(
                    messageResId = error.messageResId
                )
            } catch (error: Exception) {
                _uploadState.value = UploadMusicUiState.Error(
                    message = error.message
                )
            }
        }
    }

    fun consumeUploadState() {
        _uploadState.value = UploadMusicUiState.Idle
    }
}
