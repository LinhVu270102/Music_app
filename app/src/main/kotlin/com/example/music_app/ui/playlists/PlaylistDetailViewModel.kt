package com.example.music_app.ui.playlists

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Song
import com.example.music_app.domain.usecase.PlaylistUseCase
import com.example.music_app.data.model.isSoundCloudApiPlaylist
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

/** State and actions for a single playlist screen. */
class PlaylistDetailViewModel : ViewModel() {

    // Dependencies
    private val playlistUseCase = PlaylistUseCase()

    // Screen state
    private val _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> = _songs

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    private val _successMessageResId = MutableLiveData<Int?>()
    val successMessageResId: LiveData<Int?> = _successMessageResId

    private val _isPlaylistLiked = MutableLiveData(false)
    val isPlaylistLiked: LiveData<Boolean> = _isPlaylistLiked

    // Public screen actions
    fun isCurrentUserPlaylistOwner(
        ownerId: String,
        isSoundCloudPlaylist: Boolean
    ): Boolean {
        // Server rules remain the source of truth; this only controls the UI.
        return playlistUseCase.isCurrentUserOwner(ownerId, isSoundCloudPlaylist)
    }

    fun isSoundCloudPlaylist(playlistId: String, ownerId: String): Boolean {
        return isSoundCloudApiPlaylist(playlistId, ownerId)
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

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }

    fun clearSuccessMessage() {
        _successMessageResId.value = null
    }
}
