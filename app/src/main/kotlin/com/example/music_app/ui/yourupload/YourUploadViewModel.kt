package com.example.music_app.ui.yourupload

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

class YourUploadViewModel(
    private val repository: SongRepository = SongRepository()
) : ViewModel() {

    private val _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> = _songs

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    private val _successMessageResId = MutableLiveData<Int?>()
    val successMessageResId: LiveData<Int?> = _successMessageResId

    fun loadMyUploadedSongs() {
        viewModelScope.launch {
            try {
                publishSongs(repository.getMyUploadedSongs())
            } catch (e: AppException) {
                publishError(e.messageResId)
            } catch (_: Exception) {
                publishError(R.string.load_uploaded_songs_failed)
            }
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            try {
                repository.softDeleteMySong(song.id)
                publishSuccess(R.string.delete_song_success)
                loadMyUploadedSongs()
            } catch (e: AppException) {
                publishError(e.messageResId)
            } catch (_: Exception) {
                publishError(R.string.delete_song_failed)
            }
        }
    }

    fun toggleComments(song: Song) {
        viewModelScope.launch {
            try {
                val newAllowComments = !song.allowComments

                repository.updateMySongCommentPermission(
                    songId = song.id,
                    allowComments = newAllowComments
                )

                publishSuccess(
                    if (newAllowComments) {
                        R.string.comments_unlocked_success
                    } else {
                        R.string.comments_locked_success
                    }
                )

                loadMyUploadedSongs()
            } catch (e: AppException) {
                publishError(e.messageResId)
            } catch (_: Exception) {
                publishError(R.string.update_comment_permission_failed)
            }
        }
    }

    fun reportSong(
        song: Song,
        reason: String
    ) {
        if (reason.isBlank()) {
            publishError(R.string.report_reason_empty)
            return
        }

        viewModelScope.launch {
            try {
                repository.reportSong(
                    songId = song.id,
                    reason = reason.trim()
                )

                publishSuccess(R.string.report_success)
            } catch (e: AppException) {
                publishError(e.messageResId)
            } catch (_: Exception) {
                publishError(R.string.report_failed)
            }
        }
    }

    fun resubmitSong(song: Song) {
        viewModelScope.launch {
            try {
                repository.resubmitMyRejectedSong(song.id)
                publishSuccess(R.string.resubmit_song_success)
                loadMyUploadedSongs()
            } catch (e: AppException) {
                publishError(e.messageResId)
            } catch (_: Exception) {
                publishError(R.string.resubmit_song_failed)
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }

    fun clearSuccessMessage() {
        _successMessageResId.value = null
    }

    private fun publishSongs(songs: List<Song>) {
        _songs.value = songs
    }

    private fun publishError(messageResId: Int) {
        _errorMessageResId.value = messageResId
    }

    private fun publishSuccess(messageResId: Int) {
        _successMessageResId.value = messageResId
    }
}
