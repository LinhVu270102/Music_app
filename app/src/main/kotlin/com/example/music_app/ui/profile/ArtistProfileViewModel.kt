package com.example.music_app.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.User
import com.example.music_app.data.repository.SocialRepository
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.player.state.ArtistFollowState
import com.example.music_app.player.state.PlayerInteractionState
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

class ArtistProfileViewModel : ViewModel() {

    private val repository = SongRepository()
    private val socialRepository = SocialRepository()
    private var artistId: String = ""

    private val _artist = MutableLiveData<User?>()
    val artist: LiveData<User?> = _artist

    private val _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> = _songs

    private val _errorMessage = MutableLiveData<Int?>()
    val errorMessage: LiveData<Int?> = _errorMessage

    private val _isFollowing = MutableLiveData(false)
    val isFollowing: LiveData<Boolean> = _isFollowing

    private val _canFollow = MutableLiveData(false)
    val canFollow: LiveData<Boolean> = _canFollow

    fun loadArtistProfile(
        userId: String,
        artistName: String = ""
    ) {
        viewModelScope.launch {
            try {
                artistId = userId

                if (artistName.isNotBlank()) {
                    val artistSongs = repository.getSongsByArtistName(artistName)
                    _artist.value = User(
                        displayName = artistName,
                        username = artistName,
                        avatarUrl = artistSongs.firstOrNull()?.coverUrl.orEmpty(),
                        fullName = artistName,
                        uploadedSongsCount = artistSongs.size.toLong()
                    )
                    _songs.value = artistSongs
                    _canFollow.value = false
                    _isFollowing.value = false
                    return@launch
                }

                val artist = repository.getUserById(userId)
                _artist.value = artist
                _songs.value = repository.getSongsByUserId(userId)

                val currentUserId = repository.getCurrentUserId()
                val followable = artist != null &&
                    userId.isNotBlank() &&
                    userId != currentUserId
                _canFollow.value = followable
                _isFollowing.value = if (followable) {
                    socialRepository.isFollowing(userId)
                } else {
                    false
                }
            } catch (e: Exception) {
                _errorMessage.value = R.string.load_artist_profile_failed
            }
        }
    }

    fun toggleFollow() {
        if (artistId.isBlank() || _canFollow.value != true) return

        viewModelScope.launch {
            try {
                val followed = socialRepository.toggleFollow(artistId)
                val followerCount = runCatching {
                    socialRepository.getFollowerCount(artistId)
                }.getOrNull()

                _isFollowing.value = followed
                PlayerInteractionState.publishArtistFollow(
                    ArtistFollowState(
                        userId = artistId,
                        followed = followed,
                        followerCount = followerCount
                    )
                )
            } catch (error: AppException) {
                _errorMessage.value = error.messageResId
            } catch (_: Exception) {
                _errorMessage.value = R.string.follow_failed
            }
        }
    }

    fun applySharedFollowState(state: ArtistFollowState) {
        if (state.userId == artistId) {
            _isFollowing.value = state.followed
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
