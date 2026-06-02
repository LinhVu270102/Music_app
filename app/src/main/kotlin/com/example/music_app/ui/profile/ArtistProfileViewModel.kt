package com.example.music_app.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.User
import com.example.music_app.data.repository.SongRepository
import kotlinx.coroutines.launch

class ArtistProfileViewModel : ViewModel() {

    private val repository = SongRepository()

    private val _artist = MutableLiveData<User?>()
    val artist: LiveData<User?> = _artist

    private val _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> = _songs

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadArtistProfile(userId: String) {
        viewModelScope.launch {
            try {
                _artist.value = repository.getUserById(userId)
                _songs.value = repository.getSongsByUserId(userId)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Không tải được hồ sơ nghệ sĩ"
            }
        }
    }
}