package com.example.music_app.ui.library

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.data.repository.SoundCloudSocialRepository
import kotlinx.coroutines.launch

class LibraryViewModel : ViewModel() {

    companion object {
        private const val TAG = "LibraryViewModel"
    }

    private val repository = SongRepository()
    private val soundCloudSocialRepository = SoundCloudSocialRepository()

    private val _recentlyPlayed = MutableLiveData<List<Song>>()
    val recentlyPlayed: LiveData<List<Song>> = _recentlyPlayed

    private val _playlists = MutableLiveData<List<Playlist>>()
    val playlists: LiveData<List<Playlist>> = _playlists

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    fun loadLibraryData() {
        Log.d(TAG, "loadLibraryData called")
        loadRecentlyPlayed()
        loadMyPlaylists()
    }

    private fun loadRecentlyPlayed() {
        viewModelScope.launch {
            val start = System.currentTimeMillis()

            try {
                val songs = repository.getRecentlyPlayedSongs()
                _recentlyPlayed.value = songs

                Log.d(
                    TAG,
                    "Recently loaded: ${songs.size} songs in ${System.currentTimeMillis() - start} ms"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Recently failed", e)
                _recentlyPlayed.value = emptyList()
            }
        }
    }

    private fun loadMyPlaylists() {
        viewModelScope.launch {
            val start = System.currentTimeMillis()

            try {
                val firebasePlaylists = repository.getMyPlaylists()
                val likedPlaylists = repository.getLikedPlaylists()
                val apiPlaylists = runCatching {
                    soundCloudSocialRepository.getUserApiPlaylists()
                        .map { apiPlaylist ->
                            with(soundCloudSocialRepository) {
                                apiPlaylist.toPlaylist()
                            }
                        }
                }.getOrDefault(emptyList())

                val result = (firebasePlaylists + likedPlaylists + apiPlaylists)
                    .distinctBy { playlist -> playlist.id }
                    .sortedByDescending { playlist -> playlist.updatedAt }
                _playlists.value = result

                Log.d(
                    TAG,
                    "Playlists loaded: ${result.size} playlists in ${System.currentTimeMillis() - start} ms"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Playlists failed", e)
                _playlists.value = emptyList()
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}
