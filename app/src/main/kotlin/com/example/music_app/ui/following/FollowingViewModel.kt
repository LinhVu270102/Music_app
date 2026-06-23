package com.example.music_app.ui.following

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.User
import com.example.music_app.data.repository.SocialRepository
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

class FollowingViewModel : ViewModel() {

    private val repository = SocialRepository()

    private val _followingUsers = MutableLiveData<List<User>>(emptyList())
    val followingUsers: LiveData<List<User>> = _followingUsers

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    fun loadFollowingUsers() {
        viewModelScope.launch {
            try {
                _followingUsers.value = repository.getFollowingUsers()
            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
                _followingUsers.value = emptyList()
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.load_following_failed
                _followingUsers.value = emptyList()
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}
