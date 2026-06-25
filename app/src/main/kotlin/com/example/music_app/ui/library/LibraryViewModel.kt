package com.example.music_app.ui.library

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.PlaylistRepository
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.player.PlaybackContext
import kotlinx.coroutines.launch

class LibraryViewModel : ViewModel() {

    companion object {
        private const val TAG = "LibraryViewModel"
        private const val RECENTLY_PLAYED_LIMIT = 20
    }

    private val repository = SongRepository()
    private val playlistRepository = PlaylistRepository()

    private val _recentlyPlayed = MutableLiveData<List<Song>>()
    val recentlyPlayed: LiveData<List<Song>> = _recentlyPlayed

    private val _playlists = MutableLiveData<List<Playlist>>()
    val playlists: LiveData<List<Playlist>> = _playlists

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    fun loadLibraryData() {
        Log.d(TAG, "loadLibraryData called")
        loadRecentlyPlayed()
        loadRecentlyPlayedPlaylists()
    }

    private fun loadRecentlyPlayed() {
        viewModelScope.launch {
            val start = System.currentTimeMillis()

            try {
                val remoteSongs = repository.getRecentlyPlayedSongs()
                _recentlyPlayed.value = mergeRecentSongs(
                    remoteSongs = remoteSongs,
                    localSongs = _recentlyPlayed.value.orEmpty()
                )

                Log.d(
                    TAG,
                    "Recently loaded: ${remoteSongs.size} songs in ${System.currentTimeMillis() - start} ms"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Recently failed", e)
                _recentlyPlayed.value = emptyList()
            }
        }
    }

    private fun loadRecentlyPlayedPlaylists() {
        viewModelScope.launch {
            val start = System.currentTimeMillis()

            try {
                val result = playlistRepository.getRecentlyPlayedPlaylists()
                _playlists.value = result

                Log.d(
                    TAG,
                    "Recently played playlists loaded: ${result.size} playlists in ${System.currentTimeMillis() - start} ms"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Recently played playlists failed", e)
                _playlists.value = emptyList()
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }

    fun recordJustPlayed(song: Song) {
        _recentlyPlayed.value = (listOf(song) + _recentlyPlayed.value.orEmpty()
            .filter { item -> item.id != song.id })
            .take(RECENTLY_PLAYED_LIMIT)
    }

    fun recordJustPlayedPlaylist(context: PlaybackContext?) {
        val playbackContext = context?.takeIf { item -> item.isPlaylist } ?: return
        val playlist = Playlist(
            id = playbackContext.playlistId,
            name = playbackContext.playlistName,
            coverUrl = playbackContext.playlistCoverUrl,
            ownerId = playbackContext.playlistOwnerId,
            updatedAt = System.currentTimeMillis()
        )

        _playlists.value = (listOf(playlist) + _playlists.value.orEmpty()
            .filter { item -> item.id != playlist.id })
            .take(RECENTLY_PLAYED_LIMIT)
    }

    private fun mergeRecentSongs(
        remoteSongs: List<Song>,
        localSongs: List<Song>
    ): List<Song> {
        return (localSongs + remoteSongs)
            .distinctBy(Song::id)
            .take(RECENTLY_PLAYED_LIMIT)
    }

}
