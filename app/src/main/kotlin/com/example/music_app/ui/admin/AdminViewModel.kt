package com.example.music_app.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.SongStatus
import com.example.music_app.data.remote.FirebaseService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class AdminViewModel : ViewModel() {

    private val firebaseService = FirebaseService(FirebaseFirestore.getInstance())
    private val auth = FirebaseAuth.getInstance()

    private val _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> = _songs

    private val _selectedStatus = MutableLiveData(SongStatus.PENDING)
    val selectedStatus: LiveData<String> = _selectedStatus

    private val _messageRes = MutableLiveData<Int?>()
    val messageRes: LiveData<Int?> = _messageRes

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadSongs(status: String = _selectedStatus.value ?: SongStatus.PENDING) {
        _selectedStatus.value = status

        viewModelScope.launch {
            try {
                _isLoading.value = true

                val result = firebaseService.getSongsByStatus(status)
                _songs.value = result
            } catch (e: Exception) {
                _messageRes.value = R.string.admin_load_songs_failed
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun approveSong(songId: String) {
        updateSongStatus(
            songId = songId,
            status = SongStatus.APPROVED,
            successMessage = R.string.approve_song_success,
            failedMessage = R.string.approve_song_failed
        )
    }

    fun rejectSong(songId: String, reason: String) {
        if (reason.isBlank()) {
            _messageRes.value = R.string.reject_reason_empty
            return
        }

        updateSongStatus(
            songId = songId,
            status = SongStatus.REJECTED,
            rejectReason = reason,
            successMessage = R.string.reject_song_success,
            failedMessage = R.string.reject_song_failed
        )
    }

    fun hideSong(songId: String) {
        updateSongStatus(
            songId = songId,
            status = SongStatus.HIDDEN,
            successMessage = R.string.hide_song_success,
            failedMessage = R.string.hide_song_failed
        )
    }

    fun restoreSong(songId: String) {
        updateSongStatus(
            songId = songId,
            status = SongStatus.APPROVED,
            successMessage = R.string.restore_song_success,
            failedMessage = R.string.restore_song_failed
        )
    }

    private fun updateSongStatus(
        songId: String,
        status: String,
        rejectReason: String = "",
        successMessage: Int,
        failedMessage: Int
    ) {
        if (songId.isBlank()) return

        val adminId = auth.currentUser?.uid.orEmpty()

        viewModelScope.launch {
            try {
                _isLoading.value = true

                firebaseService.updateSongStatus(
                    songId = songId,
                    status = status,
                    reviewedBy = adminId,
                    rejectReason = rejectReason
                )

                _messageRes.value = successMessage
                loadSongs(_selectedStatus.value ?: SongStatus.PENDING)
            } catch (e: Exception) {
                _messageRes.value = failedMessage
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun doneShowMessage() {
        _messageRes.value = null
    }
}