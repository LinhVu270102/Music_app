package com.example.music_app.ui.library

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import kotlinx.coroutines.launch

class LibraryViewModel : ViewModel() {

    private val repository = SongRepository()

    private val _recentlyPlayed = MutableLiveData<List<Song>>()
    val recentlyPlayed: LiveData<List<Song>> = _recentlyPlayed

    private val _navigateEvent = MutableLiveData<String>()
    val navigateEvent: LiveData<String> = _navigateEvent

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        loadRecentlyPlayed()
    }

    fun loadRecentlyPlayed() {
        viewModelScope.launch {
            try {
                _recentlyPlayed.value =
                    repository.getRecentlyPlayedSongs()

            } catch (e: Exception) {
                _errorMessage.value = e.message

                Log.e(
                    "LibraryViewModel",
                    "Error loading recently played: ${e.message}",
                    e
                )
            }
        }
    }

    fun onSettingClicked() {
        _navigateEvent.value = "setting"
    }

    fun onYourLikesClicked() {
        _navigateEvent.value = "likes"
    }

    fun onPlaylistsClicked() {
        _navigateEvent.value = "playlists"
    }

    fun onAlbumsClicked() {
        _navigateEvent.value = "albums"
    }

    fun onFollowingClicked() {
        _navigateEvent.value = "following"
    }

    fun onYourUploadClicked() {
        _navigateEvent.value = "upload"
    }
}