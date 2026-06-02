package com.example.music_app.ui.library

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import kotlinx.coroutines.launch

class YourLikesViewModel : ViewModel() {

    private val repository = SongRepository()

    private val _likedSongs = MutableLiveData<List<Song>>()
    val likedSongs: LiveData<List<Song>> = _likedSongs

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadLikedSongs() {
        viewModelScope.launch {
            try {
                _likedSongs.value = repository.getLikedSongs()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Không tải được Your likes"
            }
        }
    }
}