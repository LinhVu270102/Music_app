package com.example.music_app.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.AdminRepository
import kotlinx.coroutines.launch

class AdminModerationViewModel(
    private val adminRepository: AdminRepository = AdminRepository()
) : ViewModel() {

    private val _pendingSongs = MutableLiveData<List<Song>>(emptyList())
    val pendingSongs: LiveData<List<Song>> = _pendingSongs

    private val _messageResId = MutableLiveData<Int?>()
    val messageResId: LiveData<Int?> = _messageResId

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadPendingSongs() {
        viewModelScope.launch {
            try {
                setLoading(true)
                publishPendingSongs(adminRepository.getPendingSongs())
            } catch (_: Exception) {
                publishMessage(R.string.load_pending_songs_failed)
            } finally {
                setLoading(false)
            }
        }
    }

    fun approveSong(song: Song) {
        viewModelScope.launch {
            try {
                adminRepository.approveSong(song.id)
                publishMessage(R.string.approved_successfully)
                loadPendingSongs()
            } catch (_: Exception) {
                publishMessage(R.string.approve_song_failed)
            }
        }
    }

    fun rejectSong(song: Song, reason: String) {
        if (reason.isBlank()) {
            publishMessage(R.string.enter_reject_reason)
            return
        }

        viewModelScope.launch {
            try {
                adminRepository.rejectSong(song.id, reason)
                publishMessage(R.string.rejected_successfully)
                loadPendingSongs()
            } catch (_: Exception) {
                publishMessage(R.string.reject_song_failed)
            }
        }
    }

    fun hideSong(song: Song) {
        viewModelScope.launch {
            try {
                adminRepository.hideSong(song.id)
                publishMessage(R.string.hide_song_success)
                loadPendingSongs()
            } catch (_: Exception) {
                publishMessage(R.string.hide_song_failed)
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
                publishMessage(R.string.update_comment_permission_success)
                loadPendingSongs()
            } catch (_: Exception) {
                publishMessage(R.string.update_comment_permission_failed)
            }
        }
    }

    fun clearMessage() {
        _messageResId.value = null
    }

    private fun publishPendingSongs(songs: List<Song>) {
        _pendingSongs.value = songs
    }

    private fun publishMessage(messageResId: Int) {
        _messageResId.value = messageResId
    }

    private fun setLoading(isLoading: Boolean) {
        _isLoading.value = isLoading
    }
}
