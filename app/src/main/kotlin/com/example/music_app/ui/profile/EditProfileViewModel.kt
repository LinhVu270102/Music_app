package com.example.music_app.ui.profile

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.data.model.User
import com.example.music_app.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditProfileViewModel : ViewModel() {

    private val userRepository = UserRepository()

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> = _user

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _updateSuccess = MutableLiveData(false)
    val updateSuccess: LiveData<Boolean> = _updateSuccess

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadCurrentUser() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)

            val result = userRepository.getCurrentUserProfile()

            withContext(Dispatchers.Main) {
                _isLoading.value = false

                result
                    .onSuccess { currentUser ->
                        _user.value = currentUser
                    }
                    .onFailure { error ->
                        _errorMessage.value = error.message
                    }
            }
        }
    }

    fun updateProfile(
        fullName: String,
        displayName: String,
        username: String,
        bio: String,
        phoneNumber: String,
        gender: String,
        country: String,
        favoriteGenres: List<String>,
        musicMoodTags: List<String>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)

            val result = userRepository.updateCurrentUserProfile(
                fullName = fullName,
                displayName = displayName,
                username = username,
                bio = bio,
                phoneNumber = phoneNumber,
                gender = gender,
                country = country,
                favoriteGenres = favoriteGenres,
                musicMoodTags = musicMoodTags
            )

            withContext(Dispatchers.Main) {
                _isLoading.value = false

                result
                    .onSuccess { updatedUser ->
                        _user.value = updatedUser
                        _updateSuccess.value = true
                    }
                    .onFailure { error ->
                        _errorMessage.value = error.message
                    }
            }
        }
    }

    fun updateAvatar(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            val result = userRepository.uploadCurrentUserAvatar(uri)

            withContext(Dispatchers.Main) {
                _isLoading.value = false
                result.onSuccess { updatedUser ->
                    _user.value = updatedUser
                }.onFailure { error ->
                    _errorMessage.value = error.message
                }
            }
        }
    }

    fun clearMessage() {
        _errorMessage.value = null
        _updateSuccess.value = false
    }
}
