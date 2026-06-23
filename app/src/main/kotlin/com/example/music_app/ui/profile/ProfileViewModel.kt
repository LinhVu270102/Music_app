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
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {

    private val songRepository = SongRepository()
    private val userRepository = UserRepository()

    private var targetUserId: String = ""

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> = _user

    private val _mySongs = MutableLiveData<List<Song>>(emptyList())
    val mySongs: LiveData<List<Song>> = _mySongs

    private val _myPlaylists = MutableLiveData<List<Playlist>>(emptyList())
    val myPlaylists: LiveData<List<Playlist>> = _myPlaylists

    private val _isOwnProfile = MutableLiveData(true)
    val isOwnProfile: LiveData<Boolean> = _isOwnProfile

    private val _isFollowing = MutableLiveData(false)
    val isFollowing: LiveData<Boolean> = _isFollowing

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<Int?>()
    val errorMessage: LiveData<Int?> = _errorMessage

    fun loadProfile(userId: String = "") {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                val currentUserId = userRepository.getCurrentUserId()
                targetUserId = userId.ifBlank { currentUserId }

                if (targetUserId.isBlank()) {
                    _user.value = null
                    _mySongs.value = emptyList()
                    _myPlaylists.value = emptyList()
                    _isOwnProfile.value = true
                    _isFollowing.value = false
                    return@launch
                }

                val isOwn = targetUserId == currentUserId
                _isOwnProfile.value = isOwn

                val userProfile =
                    if (isOwn) {
                        userRepository.getCurrentUserProfile().getOrNull()
                    } else {
                        songRepository.getUserById(targetUserId)
                    }

                val uploadedSongs =
                    if (isOwn) {
                        songRepository.getMyUploadedSongs()
                    } else {
                        songRepository.getSongsByUserId(targetUserId)
                    }

                val playlists =
                    if (isOwn) {
                        songRepository.getMyPlaylists()
                    } else {
                        songRepository.getPublicPlaylistsByUserId(targetUserId)
                    }

                val following =
                    if (!isOwn && currentUserId.isNotBlank()) {
                        songRepository.isFollowing(targetUserId)
                    } else {
                        false
                    }

                _user.value = userProfile
                _mySongs.value = uploadedSongs
                _myPlaylists.value = playlists
                _isFollowing.value = following
            } catch (_: Exception) {
                _errorMessage.value = R.string.load_profile_failed
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleFollow() {
        val userId = targetUserId
        if (userId.isBlank()) return

        viewModelScope.launch {
            try {
                val following = songRepository.toggleFollowUser(userId)
                _isFollowing.value = following
                loadProfile(userId)
            } catch (e: AppException) {
                _errorMessage.value = e.messageResId
            } catch (_: Exception) {
                _errorMessage.value = R.string.follow_user_failed
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
