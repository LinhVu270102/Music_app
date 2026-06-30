package com.example.music_app.data.repository

import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Song
import com.example.music_app.data.remote.PlaylistRemoteDataSource
import com.example.music_app.data.remote.SongRemoteDataSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** Persists local player interactions for the Firebase-backed catalog. */
class MusicInteractionRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val songRemoteDataSource: SongRemoteDataSource = SongRemoteDataSource(firestore),
    private val playlistRemoteDataSource: PlaylistRemoteDataSource =
        PlaylistRemoteDataSource(firestore)
) {

    /** Playback preparation seam for future proxy/link normalization. */
    suspend fun preparePlayableSong(song: Song): Song = song

    suspend fun saveRecentlyPlayed(song: Song) {
        val userId = currentUserIdOrNull() ?: return
        if (!song.canBeSavedToHistory()) return

        songRemoteDataSource.saveRecentlyPlayed(userId, song)
    }

    suspend fun saveRecentlyPlayedPlaylist(playlist: Playlist?) {
        val userId = currentUserIdOrNull() ?: return
        val recentPlaylist = playlist ?: return
        if (!recentPlaylist.canBeSavedToHistory()) return

        playlistRemoteDataSource.saveRecentlyPlayedPlaylist(userId, recentPlaylist)
    }

    private fun currentUserIdOrNull(): String? {
        return auth.currentUser?.uid?.takeIf(String::isNotBlank)
    }

    private fun Song.canBeSavedToHistory(): Boolean {
        return id.isNotBlank() && !isDeleted
    }

    private fun Playlist.canBeSavedToHistory(): Boolean {
        return id.isNotBlank() && name.isNotBlank()
    }
}
