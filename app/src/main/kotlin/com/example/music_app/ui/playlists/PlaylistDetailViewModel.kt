package com.example.music_app.ui.library

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

class PlaylistDetailViewModel : ViewModel() {

    private val repository = SongRepository()

    private val _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> = _songs

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    fun loadPlaylistSongs(playlistId: String) {
        viewModelScope.launch {
            try {
                _songs.value = repository.getPlaylistSongs(playlistId)
            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
            } catch (e: Exception) {
                _errorMessageResId.value = R.string.load_playlist_songs_failed
            }
        }
    }

    fun removeSongFromPlaylist(
        playlistId: String,
        songId: String
    ) {
        viewModelScope.launch {
            try {
                repository.removeSongFromPlaylist(
                    playlistId = playlistId,
                    songId = songId
                )

                loadPlaylistSongs(playlistId)
            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
            } catch (e: Exception) {
                _errorMessageResId.value = R.string.remove_song_from_playlist_failed
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}