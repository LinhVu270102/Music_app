package com.example.music_app.ui.library

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

class LibraryViewModel : ViewModel() {

    private val repository = SongRepository()

    private val _recentlyPlayed = MutableLiveData<List<Song>>()
    val recentlyPlayed: LiveData<List<Song>> = _recentlyPlayed

    private val _navigateEvent = MutableLiveData<String>()
    val navigateEvent: LiveData<String> = _navigateEvent

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    init {
        loadRecentlyPlayed()
    }

    fun loadRecentlyPlayed() {
        viewModelScope.launch {
            try {
                _recentlyPlayed.value = repository.getRecentlyPlayedSongs()
            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
                Log.e("LibraryViewModel", "Error loading recently played", e)
            } catch (e: Exception) {
                _errorMessageResId.value = R.string.load_recently_played_failed
                Log.e("LibraryViewModel", "Error loading recently played", e)
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

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}