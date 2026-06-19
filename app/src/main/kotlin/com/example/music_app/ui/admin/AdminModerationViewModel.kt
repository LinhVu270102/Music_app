package com.example.music_app.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.AdminRepository
import kotlinx.coroutines.launch

class AdminModerationViewModel : ViewModel() {

    private val adminRepository = AdminRepository()

    private val _pendingSongs = MutableLiveData<List<Song>>(emptyList())
    val pendingSongs: LiveData<List<Song>> = _pendingSongs

    private val _messageResId = MutableLiveData<Int?>()
    val messageResId: LiveData<Int?> = _messageResId

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadPendingSongs() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _pendingSongs.value = adminRepository.getPendingSongs()
            } catch (_: Exception) {
                _messageResId.value = R.string.load_pending_songs_failed
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun approveSong(song: Song) {
        viewModelScope.launch {
            try {
                adminRepository.approveSong(song.id)
                _messageResId.value = R.string.approved_successfully
                loadPendingSongs()
            } catch (_: Exception) {
                _messageResId.value = R.string.approve_song_failed
            }
        }
    }

    fun rejectSong(song: Song, reason: String) {
        if (reason.isBlank()) {
            _messageResId.value = R.string.enter_reject_reason
            return
        }

        viewModelScope.launch {
            try {
                adminRepository.rejectSong(song.id, reason)
                _messageResId.value = R.string.rejected_successfully
                loadPendingSongs()
            } catch (_: Exception) {
                _messageResId.value = R.string.reject_song_failed
            }
        }
    }

    fun hideSong(song: Song) {
        viewModelScope.launch {
            try {
                adminRepository.hideSong(song.id)
                _messageResId.value = R.string.hide_song_success
                loadPendingSongs()
            } catch (_: Exception) {
                _messageResId.value = R.string.hide_song_failed
            }
        }
    }

    fun toggleComments(song: Song) {
        viewModelScope.launch {
            try {
                adminRepository.updateSongCommentPermission(
                    songId = song.id,
                    allowComments = !song.allowComments
                )
                _messageResId.value = R.string.update_comment_permission_success
                loadPendingSongs()
            } catch (_: Exception) {
                _messageResId.value = R.string.update_comment_permission_failed
            }
        }
    }

    fun clearMessage() {
        _messageResId.value = null
    }
}