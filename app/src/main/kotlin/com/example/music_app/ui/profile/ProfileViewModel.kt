package com.example.music_app.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.User
import com.example.music_app.data.repository.PlaylistRepository
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.data.repository.SocialRepository
import com.example.music_app.data.repository.UserRepository
import com.example.music_app.player.state.ArtistFollowState
import com.example.music_app.player.state.PlayerInteractionState
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val songRepository: SongRepository = SongRepository(),
    private val playlistRepository: PlaylistRepository = PlaylistRepository(),
    private val socialRepository: SocialRepository = SocialRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

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
                    publishEmptyOwnProfile()
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
                        playlistRepository.getMyPlaylists()
                    } else {
                        playlistRepository.getPublicPlaylistsByUserId(targetUserId)
                    }

                val following =
                    if (!isOwn && currentUserId.isNotBlank()) {
                        socialRepository.isFollowing(targetUserId)
                    } else {
                        false
                    }

                val userWithLiveFollowerCount = userProfile?.let { profile ->
                    val followerCount = runCatching {
                        socialRepository.getFollowerCount(targetUserId)
                    }.getOrDefault(profile.followersCount)
                    profile.copy(followersCount = followerCount)
                }

                publishProfile(
                    user = userWithLiveFollowerCount,
                    songs = uploadedSongs,
                    playlists = playlists,
                    following = following
                )

                if (!isOwn && targetUserId.isNotBlank()) {
                    PlayerInteractionState.publishArtistFollow(
                        ArtistFollowState(
                            userId = targetUserId,
                            followed = following,
                            followerCount = userWithLiveFollowerCount?.followersCount
                        )
                    )
                }
            } catch (_: Exception) {
                publishError(R.string.load_profile_failed)
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
                val following = socialRepository.toggleFollow(userId)
                val followerCount = runCatching {
                    socialRepository.getFollowerCount(userId)
                }.getOrNull()

                PlayerInteractionState.publishArtistFollow(
                    ArtistFollowState(
                        userId = userId,
                        followed = following,
                        followerCount = followerCount
                    )
                )
                _isFollowing.value = following
                followerCount?.let { count ->
                    _user.value = _user.value?.copy(followersCount = count)
                }
            } catch (e: AppException) {
                publishError(e.messageResId)
            } catch (_: Exception) {
                publishError(R.string.follow_user_failed)
            }
        }
    }

    fun applySharedFollowState(state: ArtistFollowState) {
        if (state.userId != targetUserId) return

        _isFollowing.value = state.followed
        state.followerCount?.let { count ->
            _user.value = _user.value?.copy(followersCount = count)
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    private fun publishEmptyOwnProfile() {
        _user.value = null
        _mySongs.value = emptyList()
        _myPlaylists.value = emptyList()
        _isOwnProfile.value = true
        _isFollowing.value = false
    }

    private fun publishProfile(
        user: User?,
        songs: List<Song>,
        playlists: List<Playlist>,
        following: Boolean
    ) {
        _user.value = user
        _mySongs.value = songs
        _myPlaylists.value = playlists
        _isFollowing.value = following
    }

    private fun publishError(messageResId: Int) {
        _errorMessage.value = messageResId
    }
}
