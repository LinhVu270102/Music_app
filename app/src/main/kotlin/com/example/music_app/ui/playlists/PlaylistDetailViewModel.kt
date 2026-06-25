package com.example.music_app.ui.playlists

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SocialRepository
import com.example.music_app.domain.usecase.PlaylistUseCase
import com.example.music_app.player.state.PlayerInteractionState
import com.example.music_app.player.state.SongLikeState
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

/** State and actions for a single playlist screen. */
class PlaylistDetailViewModel : ViewModel() {

    // Dependencies
    private val playlistUseCase = PlaylistUseCase()
    private val socialRepository = SocialRepository()
    private val pendingSongLikeIds = mutableSetOf<String>()

    // Screen state
    private val _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> = _songs

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    private val _successMessageResId = MutableLiveData<Int?>()
    val successMessageResId: LiveData<Int?> = _successMessageResId

    private val _isPlaylistLiked = MutableLiveData(false)
    val isPlaylistLiked: LiveData<Boolean> = _isPlaylistLiked

    private val _songLikeStates = MutableLiveData<Map<String, Boolean>>(emptyMap())
    val songLikeStates: LiveData<Map<String, Boolean>> = _songLikeStates
    val likedSongIds: Set<String>
        get() = _songLikeStates.value.orEmpty()
            .filterValues { isLiked -> isLiked }
            .keys

    // Public screen actions
    fun isCurrentUserPlaylistOwner(
        ownerId: String
    ): Boolean {
        // Server rules remain the source of truth; this only controls the UI.
        return playlistUseCase.isCurrentUserOwner(ownerId)
    }

    fun loadPlaylistSongs(
        playlistId: String,
        ownerId: String = ""
    ) {
        viewModelScope.launch {
            try {
                _songs.value = playlistUseCase.getPlaylistSongs(playlistId, ownerId)
            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.load_playlist_songs_failed
            }
        }
    }

    fun removeSongFromPlaylist(
        playlistId: String,
        songId: String,
        ownerId: String = ""
    ) {
        viewModelScope.launch {
            try {
                playlistUseCase.removeSong(playlistId, songId, ownerId)

                loadPlaylistSongs(
                    playlistId = playlistId,
                    ownerId = ownerId
                )
            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.remove_song_from_playlist_failed
            }
        }
    }

    fun loadPlaylistLikeState(playlistId: String) {
        if (playlistId.isBlank()) return

        viewModelScope.launch {
            try {
                _isPlaylistLiked.value = playlistUseCase.isPlaylistLiked(playlistId)
            } catch (_: Exception) {
                _isPlaylistLiked.value = false
            }
        }
    }

    fun togglePlaylistLike(playlist: Playlist) {
        viewModelScope.launch {
            try {
                val isLiked = playlistUseCase.togglePlaylistLike(playlist)
                _isPlaylistLiked.value = isLiked
                _successMessageResId.value = if (isLiked) {
                    R.string.playlist_liked
                } else {
                    R.string.playlist_unliked
                }
            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.update_playlist_like_failed
            }
        }
    }

    fun addCurrentSongToPlaylist(
        playlistId: String,
        song: Song,
        ownerId: String
    ) {
        // Reload after success so the song count and list stay aligned with Firestore.
        viewModelScope.launch {
            try {
                playlistUseCase.addSong(playlistId, song)
                loadPlaylistSongs(
                    playlistId = playlistId,
                    ownerId = ownerId
                )
                _successMessageResId.value = R.string.song_added_to_playlist
            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.add_to_playlist_failed
            }
        }
    }

    fun loadSongLikeStates(songs: List<Song>) {
        if (songs.isEmpty()) {
            _songLikeStates.value = emptyMap()
            return
        }

        viewModelScope.launch {
            val likeStates = songs.associate { song ->
                song.id to runCatching {
                    PlayerInteractionState.songState(song.id)?.liked
                        ?: socialRepository.isSongLiked(song.id)
                }.getOrDefault(false)
            }
            _songLikeStates.value = likeStates

            songs.forEach { song ->
                PlayerInteractionState.publishSongLike(
                    SongLikeState(
                        songId = song.id,
                        liked = likeStates[song.id] == true,
                        likesCount = song.likes,
                        commentsCount = song.commentsCount
                    )
                )
            }
        }
    }

    fun toggleSongLike(song: Song) {
        if (song.id.isBlank()) return
        if (song.id in pendingSongLikeIds) return

        val previousState = PlayerInteractionState.songState(song.id)
        val wasLiked = _songLikeStates.value.orEmpty()[song.id]
            ?: previousState?.liked
            ?: false
        val baseLikes = previousState?.likesCount ?: song.likes
        val optimisticState = SongLikeState(
            songId = song.id,
            liked = !wasLiked,
            likesCount = nextLikesCount(baseLikes, !wasLiked),
            commentsCount = previousState?.commentsCount ?: song.commentsCount,
            changedByUser = true
        )

        pendingSongLikeIds += song.id
        PlayerInteractionState.publishSongLike(optimisticState)
        applySharedSongLikeState(optimisticState)

        viewModelScope.launch {
            try {
                val liked = socialRepository.toggleSongLike(song)
                val state = SongLikeState(
                    songId = song.id,
                    liked = liked,
                    likesCount = if (liked == optimisticState.liked) {
                        optimisticState.likesCount
                    } else {
                        nextLikesCount(baseLikes, liked)
                    },
                    commentsCount = optimisticState.commentsCount,
                    changedByUser = true
                )

                PlayerInteractionState.publishSongLike(state)
                applySharedSongLikeState(state)
            } catch (e: AppException) {
                revertSongLike(song, wasLiked, baseLikes, previousState)
                _errorMessageResId.value = e.messageResId
            } catch (_: Exception) {
                revertSongLike(song, wasLiked, baseLikes, previousState)
                _errorMessageResId.value = R.string.update_like_failed
            } finally {
                pendingSongLikeIds -= song.id
            }
        }
    }

    fun applySharedSongLikeState(state: SongLikeState) {
        _songLikeStates.value = _songLikeStates.value.orEmpty() + (
            state.songId to state.liked
        )
    }

    private fun revertSongLike(
        song: Song,
        wasLiked: Boolean,
        baseLikes: Long,
        previousState: SongLikeState?
    ) {
        val revertedState = SongLikeState(
            songId = song.id,
            liked = wasLiked,
            likesCount = baseLikes,
            commentsCount = previousState?.commentsCount ?: song.commentsCount,
            changedByUser = true
        )

        PlayerInteractionState.publishSongLike(revertedState)
        applySharedSongLikeState(revertedState)
    }

    private fun nextLikesCount(
        baseLikes: Long,
        liked: Boolean
    ): Long {
        return if (liked) baseLikes + 1 else (baseLikes - 1).coerceAtLeast(0)
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }

    fun clearSuccessMessage() {
        _successMessageResId.value = null
    }
}
