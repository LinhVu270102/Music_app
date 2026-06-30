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

class LibraryViewModel(
    private val repository: SongRepository = SongRepository(),
    private val playlistRepository: PlaylistRepository = PlaylistRepository()
) : ViewModel() {

    companion object {
        private const val TAG = "LibraryViewModel"
        private const val RECENTLY_PLAYED_LIMIT = 20
    }

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
                publishRecentlyPlayed(remoteSongs)

                Log.d(
                    TAG,
                    "Recently loaded: ${remoteSongs.size} songs in ${System.currentTimeMillis() - start} ms"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Recently failed", e)
                clearRecentlyPlayed()
            }
        }
    }

    private fun loadRecentlyPlayedPlaylists() {
        viewModelScope.launch {
            val start = System.currentTimeMillis()

            try {
                val result = playlistRepository.getRecentlyPlayedPlaylists()
                publishRecentlyPlayedPlaylists(result)

                Log.d(
                    TAG,
                    "Recently played playlists loaded: ${result.size} playlists in ${System.currentTimeMillis() - start} ms"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Recently played playlists failed", e)
                clearRecentlyPlayedPlaylists()
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }

    fun recordJustPlayed(song: Song) {
        _recentlyPlayed.value = moveSongToTop(song)
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

        _playlists.value = movePlaylistToTop(playlist)
    }

    private fun publishRecentlyPlayed(remoteSongs: List<Song>) {
        _recentlyPlayed.value = mergeRecentSongs(
            remoteSongs = remoteSongs,
            localSongs = _recentlyPlayed.value.orEmpty()
        )
    }

    private fun publishRecentlyPlayedPlaylists(playlists: List<Playlist>) {
        _playlists.value = playlists
    }

    private fun clearRecentlyPlayed() {
        _recentlyPlayed.value = emptyList()
    }

    private fun clearRecentlyPlayedPlaylists() {
        _playlists.value = emptyList()
    }

    private fun moveSongToTop(song: Song): List<Song> {
        return (listOf(song) + _recentlyPlayed.value.orEmpty()
            .filter { item -> item.id != song.id })
            .take(RECENTLY_PLAYED_LIMIT)
    }

    private fun movePlaylistToTop(playlist: Playlist): List<Playlist> {
        return (listOf(playlist) + _playlists.value.orEmpty()
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
