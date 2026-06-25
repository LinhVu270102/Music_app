package com.example.music_app.ui.playlists

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.repository.PlaylistRepository
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

class PlaylistsViewModel : ViewModel() {

    private val repository = PlaylistRepository()

    private val _playlists = MutableLiveData<List<Playlist>>()
    val playlists: LiveData<List<Playlist>> = _playlists

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    fun loadPlaylists() {
        viewModelScope.launch {
            try {
                val myPlaylists = repository.getMyPlaylists()
                val likedPlaylists = repository.getLikedPlaylists()
                val currentUserId = repository.getCurrentUserId()
                val ownPlaylistIds = myPlaylists.mapTo(mutableSetOf()) { playlist ->
                    playlist.id
                }

                _playlists.value =
                    (myPlaylists + likedPlaylists)
                        .distinctBy { playlist -> playlist.id }
                        .sortedWith(
                            compareByDescending<Playlist> { playlist ->
                                playlist.id in ownPlaylistIds ||
                                    playlist.ownerId == currentUserId
                            }.thenByDescending(Playlist::updatedAt)
                        )
            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.load_playlists_failed
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            try {
                repository.createPlaylist(name)
                loadPlaylists()
            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
            } catch (e: Exception) {
                _errorMessageResId.value = R.string.create_playlist_failed
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            try {
                repository.deletePlaylist(playlistId)
                loadPlaylists()
            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
            } catch (e: Exception) {
                _errorMessageResId.value = R.string.delete_playlist_failed
            }
        }
    }

    fun canDeletePlaylist(playlist: Playlist): Boolean {
        return playlist.ownerId.isNotBlank() && playlist.ownerId == repository.getCurrentUserId()
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}
