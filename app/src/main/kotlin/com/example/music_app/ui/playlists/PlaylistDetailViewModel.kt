package com.example.music_app.ui.library

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import kotlinx.coroutines.launch

class PlaylistDetailViewModel : ViewModel() {

    private val repository = SongRepository()

    private val _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> = _songs

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadPlaylistSongs(playlistId: String) {
        viewModelScope.launch {
            try {
                _songs.value = repository.getPlaylistSongs(playlistId)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Không tải được bài hát trong playlist"
            }
        }
    }
}