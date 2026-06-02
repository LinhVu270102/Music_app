package com.example.music_app.ui.library

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.data.model.User
import com.example.music_app.data.repository.SongRepository
import kotlinx.coroutines.launch

class FollowingViewModel : ViewModel() {

    private val repository = SongRepository()

    private val _followingUsers = MutableLiveData<List<User>>()
    val followingUsers: LiveData<List<User>> = _followingUsers

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadFollowingUsers() {
        viewModelScope.launch {
            try {
                _followingUsers.value = repository.getFollowingUsers()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Không tải được danh sách following"
            }
        }
    }
}