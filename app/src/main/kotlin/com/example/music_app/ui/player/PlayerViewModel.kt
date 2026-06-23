package com.example.music_app.ui.player

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.data.repository.SoundCloudSocialRepository
import com.example.music_app.player.state.ArtistFollowState
import com.example.music_app.player.state.PlayerInteractionState
import com.example.music_app.player.state.SongLikeState
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

/** Full-player state and interactions. The Fragment only observes this ViewModel. */
class PlayerViewModel : ViewModel() {

    // Dependencies
    private val repository = SongRepository()
    private val soundCloudSocialRepository = SoundCloudSocialRepository()

    // Screen state
    private val _song = MutableLiveData<Song?>()
    val song: LiveData<Song?> = _song

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    private val _successMessageResId = MutableLiveData<Int?>()
    val successMessageResId: LiveData<Int?> = _successMessageResId

    // Public screen actions
    fun isCurrentUserSongOwner(song: Song): Boolean {
        return song.uploaderId.isNotBlank() &&
                song.uploaderId == repository.getCurrentUserId()
    }

    fun isCurrentUser(userId: String): Boolean {
        return userId.isNotBlank() && userId == repository.getCurrentUserId()
    }

    fun loadSong(songId: String) {
        viewModelScope.launch {
            try {
                _song.value = repository.getSong(songId)
            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
                Log.e("PlayerViewModel", "Error loading song", e)
            } catch (e: Exception) {
                _errorMessageResId.value = R.string.load_song_failed
                Log.e("PlayerViewModel", "Error loading song", e)
            }
        }
    }

    fun loadLikeState(song: Song) {
        // SoundCloud and Firebase keep social data in different stores.
        viewModelScope.launch {
            runCatching {
                if (soundCloudSocialRepository.isSoundCloudSong(song)) {
                    val social = soundCloudSocialRepository.getTrackSocial(song.id)
                    SongLikeState(song.id, social.liked, social.likesCount, social.commentsCount)
                } else {
                    val cached = PlayerInteractionState.songState(song.id)
                    SongLikeState(
                        song.id,
                        repository.isSongLiked(song.id),
                        cached?.likesCount ?: song.likes,
                        cached?.commentsCount ?: song.commentsCount
                    )
                }
            }.getOrNull()?.let(PlayerInteractionState::publishSongLike)
        }
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch {
            try {
                val state = if (soundCloudSocialRepository.isSoundCloudSong(song)) {
                    val social = soundCloudSocialRepository.toggleTrackLike(song.id)
                    SongLikeState(song.id, social.liked, social.likesCount, social.commentsCount)
                } else {
                    val liked = repository.toggleLikeSong(song)
                    val cached = PlayerInteractionState.songState(song.id)
                    val count = cached?.likesCount ?: song.likes
                    SongLikeState(
                        song.id,
                        liked,
                        if (liked) count + 1 else (count - 1).coerceAtLeast(0),
                        cached?.commentsCount ?: song.commentsCount
                    )
                }

                PlayerInteractionState.publishSongLike(state)
                _successMessageResId.value = if (state.liked) {
                    R.string.added_to_your_likes
                } else {
                    R.string.removed_from_your_likes
                }
            } catch (error: AppException) {
                _errorMessageResId.value = error.messageResId
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.update_like_failed
            }
        }
    }

    fun loadFollowState(userId: String) {
        if (userId.isBlank() || isCurrentUser(userId)) return

        viewModelScope.launch {
            runCatching {
                ArtistFollowState(
                    userId = userId,
                    followed = repository.isFollowing(userId),
                    followerCount = repository.getFollowerCount(userId)
                )
            }.getOrNull()?.let(PlayerInteractionState::publishArtistFollow)
        }
    }

    fun toggleFollow(userId: String) {
        if (userId.isBlank()) {
            _errorMessageResId.value = R.string.invalid_user
            return
        }

        viewModelScope.launch {
            try {
                val followed = repository.toggleFollowUser(userId)
                PlayerInteractionState.publishArtistFollow(
                    ArtistFollowState(
                        userId = userId,
                        followed = followed,
                        followerCount = repository.getFollowerCount(userId)
                    )
                )
                _successMessageResId.value = if (followed) {
                    R.string.follow_artist_success
                } else {
                    R.string.unfollow_artist_success
                }
            } catch (error: AppException) {
                _errorMessageResId.value = error.messageResId
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.follow_failed
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }

    fun clearSuccessMessage() {
        _successMessageResId.value = null
    }
}
