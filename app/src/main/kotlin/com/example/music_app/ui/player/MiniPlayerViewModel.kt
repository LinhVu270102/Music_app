package com.example.music_app.ui.player

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.AuthRepository
import com.example.music_app.data.repository.SocialRepository
import com.example.music_app.player.state.ArtistFollowState
import com.example.music_app.player.state.PlayerInteractionState
import com.example.music_app.player.state.SongLikeState
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

/** Social state for the mini player. The Activity only renders shared player state. */
class MiniPlayerViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val socialRepository: SocialRepository = SocialRepository()
) : ViewModel() {

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    private val _successMessageResId = MutableLiveData<Int?>()
    val successMessageResId: LiveData<Int?> = _successMessageResId

    private val _isFollowButtonVisible = MutableLiveData(true)
    val isFollowButtonVisible: LiveData<Boolean> = _isFollowButtonVisible

    fun loadLikeState(song: Song) {
        viewModelScope.launch {
            runCatching {
                val cached = PlayerInteractionState.songState(song.id)
                SongLikeState(
                    songId = song.id,
                    liked = socialRepository.isSongLiked(song.id),
                    likesCount = cached?.likesCount ?: song.likes,
                    commentsCount = cached?.commentsCount ?: song.commentsCount
                )
            }.getOrNull()?.let(PlayerInteractionState::publishSongLike)
        }
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch {
            try {
                val liked = socialRepository.toggleSongLike(song)
                val cached = PlayerInteractionState.songState(song.id)
                val count = cached?.likesCount ?: song.likes
                val state = SongLikeState(
                    songId = song.id,
                    liked = liked,
                    likesCount = if (liked) count + 1 else (count - 1).coerceAtLeast(0),
                    commentsCount = cached?.commentsCount ?: song.commentsCount,
                    changedByUser = true
                )

                PlayerInteractionState.publishSongLike(state)
                publishSuccess(if (state.liked) {
                    R.string.added_to_your_likes
                } else {
                    R.string.removed_from_your_likes
                })
            } catch (error: AppException) {
                publishError(error.messageResId)
            } catch (_: Exception) {
                publishError(R.string.update_like_failed)
            }
        }
    }

    fun loadFollowState(song: Song) {
        val uploaderId = song.uploaderId
        val currentUserId = authRepository.getCurrentUser()?.uid

        if (uploaderId.isBlank() || currentUserId == uploaderId) {
            _isFollowButtonVisible.value = false
            return
        }

        _isFollowButtonVisible.value = true
        viewModelScope.launch {
            runCatching {
                val followed = socialRepository.isFollowing(uploaderId)
                ArtistFollowState(
                    userId = uploaderId,
                    followed = followed,
                    followerCount = runCatching {
                        socialRepository.getFollowerCount(uploaderId)
                    }.getOrNull()
                )
            }.getOrNull()?.let(PlayerInteractionState::publishArtistFollow)
        }
    }

    fun toggleFollow(song: Song) {
        val uploaderId = song.uploaderId
        val currentUserId = authRepository.getCurrentUser()?.uid

        when {
            uploaderId.isBlank() -> {
                publishError(R.string.target_user_not_found)
                return
            }

            currentUserId == uploaderId -> {
                publishError(R.string.cannot_follow_yourself)
                return
            }
        }

        viewModelScope.launch {
            try {
                val followed = socialRepository.toggleFollow(uploaderId)
                val followerCount = runCatching {
                    socialRepository.getFollowerCount(uploaderId)
                }.getOrNull()
                PlayerInteractionState.publishArtistFollow(
                    ArtistFollowState(
                        userId = uploaderId,
                        followed = followed,
                        followerCount = followerCount
                    )
                )
                publishSuccess(if (followed) {
                    R.string.followed_successfully
                } else {
                    R.string.unfollowed_successfully
                })
            } catch (error: AppException) {
                publishError(error.messageResId)
            } catch (_: Exception) {
                publishError(R.string.follow_failed)
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }

    fun clearSuccessMessage() {
        _successMessageResId.value = null
    }

    private fun publishError(messageResId: Int) {
        _errorMessageResId.value = messageResId
    }

    private fun publishSuccess(messageResId: Int) {
        _successMessageResId.value = messageResId
    }
}
