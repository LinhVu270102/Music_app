package com.example.music_app.ui.library

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.User
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

class FollowingViewModel : ViewModel() {

    private val repository = SongRepository()

    private val _followingUsers = MutableLiveData<List<User>>()
    val followingUsers: LiveData<List<User>> = _followingUsers

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    fun loadFollowingUsers() {
        viewModelScope.launch {
            try {
                _followingUsers.value = repository.getFollowingUsers()
            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
            } catch (e: Exception) {
                _errorMessageResId.value = R.string.load_following_failed
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}