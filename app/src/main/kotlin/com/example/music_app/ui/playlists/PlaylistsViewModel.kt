package com.example.music_app.ui.library

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.repository.SongRepository
import kotlinx.coroutines.launch

class PlaylistsViewModel : ViewModel() {

    private val repository = SongRepository()

    private val _playlists = MutableLiveData<List<Playlist>>()
    val playlists: LiveData<List<Playlist>> = _playlists

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadPlaylists() {
        viewModelScope.launch {
            try {
                _playlists.value = repository.getMyPlaylists()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Không tải được playlist"
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            try {
                repository.createPlaylist(name)
                loadPlaylists()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Không tạo được playlist"
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            try {
                repository.deletePlaylist(playlistId)
                loadPlaylists()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Không xoá được playlist"
            }
        }
    }
}