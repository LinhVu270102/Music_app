package com.example.music_app.ui.player

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.PlaylistRepository
import com.example.music_app.data.repository.SocialRepository
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.player.PlayerManager
import com.example.music_app.player.state.ArtistFollowState
import com.example.music_app.player.state.PlayerInteractionState
import com.example.music_app.player.state.SongLikeState
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

/** Full-player state and interactions. The Fragment only observes this ViewModel. */
class PlayerViewModel(
    private val repository: SongRepository = SongRepository(),
    private val playlistRepository: PlaylistRepository = PlaylistRepository(),
    private val socialRepository: SocialRepository = SocialRepository()
) : ViewModel() {

    // Screen state
    private val _song = MutableLiveData<Song?>()
    val song: LiveData<Song?> = _song

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    private val _successMessageResId = MutableLiveData<Int?>()
    val successMessageResId: LiveData<Int?> = _successMessageResId

    private val _songDeleted = MutableLiveData(false)
    val songDeleted: LiveData<Boolean> = _songDeleted

    private val _playlistPickerState = MutableLiveData<PlayerPlaylistPickerState?>()
    val playlistPickerState: LiveData<PlayerPlaylistPickerState?> = _playlistPickerState

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
                publishError(e.messageResId)
                Log.e("PlayerViewModel", "Error loading song", e)
            } catch (e: Exception) {
                publishError(R.string.load_song_failed)
                Log.e("PlayerViewModel", "Error loading song", e)
            }
        }
    }

    fun loadLikeState(song: Song) {
        viewModelScope.launch {
            runCatching {
                val cached = PlayerInteractionState.songState(song.id)
                SongLikeState(
                    song.id,
                    socialRepository.isSongLiked(song.id),
                    cached?.likesCount ?: song.likes,
                    cached?.commentsCount ?: song.commentsCount
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
                    song.id,
                    liked,
                    if (liked) count + 1 else (count - 1).coerceAtLeast(0),
                    cached?.commentsCount ?: song.commentsCount,
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

    fun loadFollowState(userId: String) {
        if (userId.isBlank() || isCurrentUser(userId)) return

        viewModelScope.launch {
            runCatching {
                val followed = socialRepository.isFollowing(userId)
                ArtistFollowState(
                    userId = userId,
                    followed = followed,
                    followerCount = runCatching {
                        socialRepository.getFollowerCount(userId)
                    }.getOrNull()
                )
            }.getOrNull()?.let(PlayerInteractionState::publishArtistFollow)
        }
    }

    fun toggleFollow(userId: String) {
        if (userId.isBlank()) {
            publishError(R.string.invalid_user)
            return
        }

        viewModelScope.launch {
            try {
                val followed = socialRepository.toggleFollow(userId)
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
                publishSuccess(if (followed) {
                    R.string.follow_artist_success
                } else {
                    R.string.unfollow_artist_success
                })
            } catch (error: AppException) {
                publishError(error.messageResId)
            } catch (_: Exception) {
                publishError(R.string.follow_failed)
            }
        }
    }

    fun deleteSong(songId: String) {
        if (songId.isBlank()) {
            publishError(R.string.invalid_song)
            return
        }

        viewModelScope.launch {
            try {
                repository.softDeleteMySong(songId)
                _songDeleted.value = true
            } catch (error: AppException) {
                publishError(error.messageResId)
            } catch (_: Exception) {
                publishError(R.string.delete_song_failed)
            }
        }
    }

    fun toggleSongComments(song: Song) {
        if (song.id.isBlank()) {
            publishError(R.string.invalid_song)
            return
        }

        viewModelScope.launch {
            try {
                repository.updateMySongCommentPermission(
                    songId = song.id,
                    allowComments = !song.allowComments
                )
                publishSuccess(if (song.allowComments) {
                    R.string.comments_locked_success
                } else {
                    R.string.comments_unlocked_success
                })
                _song.value = repository.getSong(song.id)
            } catch (error: AppException) {
                publishError(error.messageResId)
            } catch (_: Exception) {
                publishError(R.string.update_comment_permission_failed)
            }
        }
    }

    fun reportSong(songId: String, reason: String, description: String = "") {
        if (reason.isBlank()) {
            publishError(R.string.report_reason_empty)
            return
        }

        viewModelScope.launch {
            try {
                repository.reportSong(songId, reason, description)
                publishSuccess(R.string.report_song_success)
            } catch (error: AppException) {
                publishError(error.messageResId)
            } catch (_: Exception) {
                publishError(R.string.report_song_failed)
            }
        }
    }

    fun requestPlaylistPicker(song: Song) {
        viewModelScope.launch {
            try {
                val activePlaylistId = PlayerManager.playbackContext.value?.playlistId
                val options = playlistRepository.getMyPlaylists()
                    .filterNot { playlist -> playlist.id == activePlaylistId }
                    .map { playlist ->
                    PlayerPlaylistOption(
                        id = playlist.id,
                        name = playlist.name,
                        songsCount = playlist.songsCount
                    )
                }

                _playlistPickerState.value = PlayerPlaylistPickerState(song, options)
            } catch (error: AppException) {
                publishError(error.messageResId)
            } catch (_: Exception) {
                publishError(R.string.load_playlists_failed)
            }
        }
    }

    fun createPlaylistAndAddSong(
        name: String,
        song: Song
    ) {
        if (name.isBlank()) {
            publishError(R.string.playlist_name_empty)
            return
        }

        viewModelScope.launch {
            try {
                val playlist = playlistRepository.createPlaylist(name)
                playlistRepository.addSongToPlaylist(playlist.id, song)
                publishSuccess(R.string.added_to_playlist_success)
            } catch (error: AppException) {
                publishError(error.messageResId)
            } catch (_: Exception) {
                publishError(R.string.add_to_playlist_failed)
            }
        }
    }

    fun addSongToPlaylist(
        playlistId: String,
        song: Song
    ) {
        if (playlistId.isBlank()) {
            publishError(R.string.playlist_not_found)
            return
        }
        if (playlistId == PlayerManager.playbackContext.value?.playlistId) {
            publishError(R.string.song_already_in_current_playlist)
            return
        }

        viewModelScope.launch {
            try {
                playlistRepository.addSongToPlaylist(playlistId, song)
                publishSuccess(R.string.added_to_playlist_success)
            } catch (error: AppException) {
                publishError(error.messageResId)
            } catch (_: Exception) {
                publishError(R.string.add_song_to_playlist_failed)
            }
        }
    }

    fun consumePlaylistPickerState() {
        _playlistPickerState.value = null
    }

    fun consumeSongDeleted() {
        _songDeleted.value = false
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
