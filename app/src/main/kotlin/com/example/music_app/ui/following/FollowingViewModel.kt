package com.example.music_app.ui.following

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.User
import com.example.music_app.data.repository.SocialRepository
import com.example.music_app.player.state.ArtistFollowState
import com.example.music_app.player.state.PlayerInteractionState
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

class FollowingViewModel(
    private val repository: SocialRepository = SocialRepository()
) : ViewModel() {

    private val _followingUsers = MutableLiveData<List<User>>(emptyList())
    val followingUsers: LiveData<List<User>> = _followingUsers

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    private val followObserver = androidx.lifecycle.Observer<ArtistFollowState> {
        loadFollowingUsers()
    }

    init {
        PlayerInteractionState.artistFollowUpdates.observeForever(followObserver)
    }

    fun loadFollowingUsers() {
        viewModelScope.launch {
            try {
                publishFollowingUsers(repository.getFollowingUsers())
            } catch (e: AppException) {
                publishError(e.messageResId)
                publishFollowingUsers(emptyList())
            } catch (_: Exception) {
                publishError(R.string.load_following_failed)
                publishFollowingUsers(emptyList())
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }

    override fun onCleared() {
        PlayerInteractionState.artistFollowUpdates.removeObserver(followObserver)
        super.onCleared()
    }

    private fun publishFollowingUsers(users: List<User>) {
        _followingUsers.value = users
    }

    private fun publishError(messageResId: Int) {
        _errorMessageResId.value = messageResId
    }
}
