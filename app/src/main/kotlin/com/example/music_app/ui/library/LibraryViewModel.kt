package com.example.music_app.ui.library

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

data class MusicItem(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String
)

class LibraryViewModel : ViewModel() {

    private val _recentlyPlayed = MutableLiveData<List<MusicItem>>()
    val recentlyPlayed: LiveData<List<MusicItem>> = _recentlyPlayed

    private val _listeningHistory = MutableLiveData<List<MusicItem>>()
    val listeningHistory: LiveData<List<MusicItem>> = _listeningHistory

    private val _navigateEvent = MutableLiveData<String>()
    val navigateEvent: LiveData<String> = _navigateEvent

    init {
        _recentlyPlayed.value = listOf(
            MusicItem("1", "Song A", "Artist A", "https://example.com/coverA.jpg"),
            MusicItem("2", "Song B", "Artist B", "https://example.com/coverB.jpg")
        )

        _listeningHistory.value = listOf(
            MusicItem("3", "Song C", "Artist C", "https://example.com/coverC.jpg"),
            MusicItem("4", "Song D", "Artist D", "https://example.com/coverD.jpg")
        )
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