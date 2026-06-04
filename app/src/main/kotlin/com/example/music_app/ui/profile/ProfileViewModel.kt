package com.example.music_app.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.User
import com.example.music_app.data.repository.SongRepository
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {

    private val repository = SongRepository()

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> = _user

    private val _mySongs = MutableLiveData<List<Song>>()
    val mySongs: LiveData<List<Song>> = _mySongs

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<Int?>()
    val errorMessage: LiveData<Int?> = _errorMessage

    fun loadProfile() {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                val userProfile = repository.getCurrentUserProfile()
                val uploadedSongs = repository.getMyUploadedSongs()

                _user.value = userProfile
                _mySongs.value = uploadedSongs
            } catch (e: Exception) {
                _errorMessage.value = R.string.load_profile_failed
            } finally {
                _isLoading.value = false
            }
        }
    }
}