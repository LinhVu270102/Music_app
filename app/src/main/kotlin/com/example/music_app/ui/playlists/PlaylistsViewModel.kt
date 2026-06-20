package com.example.music_app.ui.library

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch
import com.example.music_app.data.repository.SoundCloudSocialRepository

class PlaylistsViewModel : ViewModel() {

    private val repository = SongRepository()
    private val soundCloudSocialRepository = SoundCloudSocialRepository()

    private val _playlists = MutableLiveData<List<Playlist>>()
    val playlists: LiveData<List<Playlist>> = _playlists

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    fun loadPlaylists() {
        viewModelScope.launch {
            try {
                val firebasePlaylists = repository.getMyPlaylists()

                val apiPlaylists =
                    try {
                        soundCloudSocialRepository.getUserApiPlaylists()
                            .map { playlist ->
                                with(soundCloudSocialRepository) {
                                    playlist.toPlaylist()
                                }
                            }
                    } catch (_: Exception) {
                        emptyList()
                    }

                _playlists.value =
                    (firebasePlaylists + apiPlaylists)
                        .sortedByDescending { playlist ->
                            playlist.updatedAt
                        }
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

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}