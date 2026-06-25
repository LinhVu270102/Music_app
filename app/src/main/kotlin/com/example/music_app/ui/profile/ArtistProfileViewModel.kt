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
                    val resolvedArtistId = artistSongs
                        .firstOrNull { song -> song.uploaderId.isNotBlank() }
                        ?.uploaderId
                        .orEmpty()
                    val currentUserId = repository.getCurrentUserId()
                    val followable = resolvedArtistId.isNotBlank() &&
                        resolvedArtistId != currentUserId

                    artistId = resolvedArtistId

                    _artist.value = User(
                        uid = resolvedArtistId,
                        displayName = artistName,
                        username = artistName,
                        avatarUrl = artistSongs.firstOrNull()?.coverUrl.orEmpty(),
                        fullName = artistName,
                        uploadedSongsCount = artistSongs.size.toLong()
                    )
                    _songs.value = artistSongs
                    _canFollow.value = followable
                    _isFollowing.value = if (followable) {
                        val cachedState = PlayerInteractionState.artistState(resolvedArtistId)
                        cachedState?.followed ?: socialRepository.isFollowing(resolvedArtistId)
                    } else {
                        false
                    }

                    if (followable) {
                        publishCurrentFollowState(resolvedArtistId)
                    }
                    return@launch
                }

                val artistSongs = repository.getSongsByUserId(userId)
                val artist = repository.getUserById(userId) ?: artistSongs.firstOrNull()?.let { song ->
                    User(
                        uid = userId,
                        displayName = song.artist,
                        username = song.artist,
                        avatarUrl = song.coverUrl,
                        fullName = song.artist,
                        uploadedSongsCount = artistSongs.size.toLong()
                    )
                }

                _artist.value = artist
                _songs.value = artistSongs

                val currentUserId = repository.getCurrentUserId()
                val followable = userId.isNotBlank() &&
                    userId != currentUserId
                _canFollow.value = followable
                _isFollowing.value = if (followable) {
                    val cachedState = PlayerInteractionState.artistState(userId)
                    cachedState?.followed ?: socialRepository.isFollowing(userId)
                } else {
                    false
                }

                if (followable) {
                    publishCurrentFollowState(userId)
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

    private suspend fun publishCurrentFollowState(userId: String) {
        val followed = _isFollowing.value == true
        val followerCount = runCatching {
            socialRepository.getFollowerCount(userId)
        }.getOrNull()

        PlayerInteractionState.publishArtistFollow(
            ArtistFollowState(
                userId = userId,
                followed = followed,
                followerCount = followerCount
            )
        )
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
