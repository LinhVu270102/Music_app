package com.example.music_app.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.User
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.data.repository.UserRepository
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {

    private val songRepository = SongRepository()
    private val userRepository = UserRepository()

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> = _user

    private val _mySongs = MutableLiveData<List<Song>>(emptyList())
    val mySongs: LiveData<List<Song>> = _mySongs

    private val _myPlaylists = MutableLiveData<List<Playlist>>(emptyList())
    val myPlaylists: LiveData<List<Playlist>> = _myPlaylists

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<Int?>()
    val errorMessage: LiveData<Int?> = _errorMessage

    fun loadProfile() {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                val userProfile = userRepository.getCurrentUserProfile().getOrNull()
                val uploadedSongs = songRepository.getMyUploadedSongs()
                val playlists = songRepository.getMyPlaylists()

                _user.value = userProfile
                _mySongs.value = uploadedSongs
                _myPlaylists.value = playlists
            } catch (_: Exception) {
                _errorMessage.value = R.string.load_profile_failed
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}