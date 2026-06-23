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
import com.example.music_app.data.repository.SoundCloudPlaylistRepository
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
    private val playlistRepository = PlaylistRepository()
    private val socialRepository = SocialRepository()
    private val soundCloudSocialRepository = SoundCloudSocialRepository()
    private val soundCloudPlaylistRepository = SoundCloudPlaylistRepository()

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
                        socialRepository.isSongLiked(song.id),
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
                    val liked = socialRepository.toggleSongLike(song)
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
                    followed = socialRepository.isFollowing(userId),
                    followerCount = socialRepository.getFollowerCount(userId)
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
                val followed = socialRepository.toggleFollow(userId)
                PlayerInteractionState.publishArtistFollow(
                    ArtistFollowState(
                        userId = userId,
                        followed = followed,
                        followerCount = socialRepository.getFollowerCount(userId)
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

    fun deleteSong(songId: String) {
        if (songId.isBlank()) {
            _errorMessageResId.value = R.string.invalid_song
            return
        }

        viewModelScope.launch {
            try {
                repository.softDeleteMySong(songId)
                _songDeleted.value = true
            } catch (error: AppException) {
                _errorMessageResId.value = error.messageResId
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.delete_song_failed
            }
        }
    }

    fun toggleSongComments(song: Song) {
        if (song.id.isBlank()) {
            _errorMessageResId.value = R.string.invalid_song
            return
        }

        viewModelScope.launch {
            try {
                repository.updateMySongCommentPermission(
                    songId = song.id,
                    allowComments = !song.allowComments
                )
                _successMessageResId.value = if (song.allowComments) {
                    R.string.comments_locked_success
                } else {
                    R.string.comments_unlocked_success
                }
                _song.value = repository.getSong(song.id)
            } catch (error: AppException) {
                _errorMessageResId.value = error.messageResId
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.update_comment_permission_failed
            }
        }
    }

    fun reportSong(songId: String, reason: String, description: String = "") {
        if (reason.isBlank()) {
            _errorMessageResId.value = R.string.report_reason_empty
            return
        }

        viewModelScope.launch {
            try {
                repository.reportSong(songId, reason, description)
                _successMessageResId.value = R.string.report_song_success
            } catch (error: AppException) {
                _errorMessageResId.value = error.messageResId
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.report_song_failed
            }
        }
    }

    fun requestPlaylistPicker(song: Song) {
        viewModelScope.launch {
            try {
                val isSoundCloudSong = soundCloudSocialRepository.isSoundCloudSong(song)
                val source = if (isSoundCloudSong) {
                    PlayerPlaylistSource.SOUNDCLOUD
                } else {
                    PlayerPlaylistSource.FIREBASE
                }

                val options = if (isSoundCloudSong) {
                    soundCloudPlaylistRepository.getUserApiPlaylists().map { playlist ->
                        PlayerPlaylistOption(
                            id = playlist.id,
                            name = playlist.name,
                            songsCount = playlist.songsCount.toLong()
                        )
                    }
                } else {
                    playlistRepository.getMyPlaylists().map { playlist ->
                        PlayerPlaylistOption(
                            id = playlist.id,
                            name = playlist.name,
                            songsCount = playlist.songsCount
                        )
                    }
                }

                _playlistPickerState.value = PlayerPlaylistPickerState(song, source, options)
            } catch (error: AppException) {
                _errorMessageResId.value = error.messageResId
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.load_playlists_failed
            }
        }
    }

    fun createPlaylistAndAddSong(
        name: String,
        song: Song,
        source: PlayerPlaylistSource
    ) {
        if (name.isBlank()) {
            _errorMessageResId.value = R.string.playlist_name_empty
            return
        }

        viewModelScope.launch {
            try {
                if (source == PlayerPlaylistSource.SOUNDCLOUD) {
                    val playlist = soundCloudPlaylistRepository.createUserApiPlaylist(name)
                    soundCloudPlaylistRepository.addTrackToUserApiPlaylist(playlist.id, song)
                } else {
                    val playlist = playlistRepository.createPlaylist(name)
                    playlistRepository.addSongToPlaylist(playlist.id, song)
                }
                _successMessageResId.value = R.string.added_to_playlist_success
            } catch (error: AppException) {
                _errorMessageResId.value = error.messageResId
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.add_to_playlist_failed
            }
        }
    }

    fun addSongToPlaylist(
        playlistId: String,
        song: Song,
        source: PlayerPlaylistSource
    ) {
        if (playlistId.isBlank()) {
            _errorMessageResId.value = R.string.playlist_not_found
            return
        }

        viewModelScope.launch {
            try {
                if (source == PlayerPlaylistSource.SOUNDCLOUD) {
                    soundCloudPlaylistRepository.addTrackToUserApiPlaylist(playlistId, song)
                } else {
                    playlistRepository.addSongToPlaylist(playlistId, song)
                }
                _successMessageResId.value = R.string.added_to_playlist_success
            } catch (error: AppException) {
                _errorMessageResId.value = error.messageResId
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.add_song_to_playlist_failed
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
}
