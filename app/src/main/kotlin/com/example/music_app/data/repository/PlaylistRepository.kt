package com.example.music_app.data.repository

import com.example.music_app.R
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.SongStatus
import com.example.music_app.data.remote.PlaylistRemoteDataSource
import com.example.music_app.utils.AppException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Owns Firestore-backed playlist data for the signed-in user.
 */
class PlaylistRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val remoteDataSource: PlaylistRemoteDataSource = PlaylistRemoteDataSource(firestore)
) {

    fun getCurrentUserId(): String = auth.currentUser?.uid.orEmpty()

    suspend fun createPlaylist(
        name: String,
        description: String = ""
    ): Playlist {
        val userId = requireCurrentUserId()
        return remoteDataSource.create(userId, name, description)
    }

    suspend fun getMyPlaylists(): List<Playlist> {
        val userId = getCurrentUserId()
        return if (userId.isBlank()) {
            emptyList()
        } else {
            remoteDataSource.getUserPlaylists(userId)
                .map { playlist -> playlist.ensureOwner(userId) }
        }
    }

    suspend fun getLibraryPlaylists(): List<Playlist> {
        val userId = getCurrentUserId()
        if (userId.isBlank()) return emptyList()

        val myPlaylists = getMyPlaylists()
        val likedPlaylists = getLikedPlaylists()
        val ownedPlaylistIds = myPlaylists.mapTo(mutableSetOf()) { playlist ->
            playlist.id
        }

        return (myPlaylists + likedPlaylists)
            .distinctBy { playlist -> playlist.id }
            .sortedWith(
                compareByDescending<Playlist> { playlist ->
                    playlist.id in ownedPlaylistIds || playlist.ownerId == userId
                }.thenByDescending(Playlist::updatedAt)
                    .thenBy { playlist -> playlist.name.lowercase() }
            )
    }

    suspend fun getRecentlyPlayedPlaylists(): List<Playlist> {
        val userId = getCurrentUserId()
        return if (userId.isBlank()) {
            emptyList()
        } else {
            remoteDataSource.getRecentlyPlayedPlaylists(userId)
        }
    }

    suspend fun getPublicPlaylistsByUserId(userId: String): List<Playlist> {
        if (userId.isBlank()) return emptyList()
        return remoteDataSource.getPublicUserPlaylists(userId)
    }

    suspend fun deletePlaylist(playlistId: String) {
        val userId = getCurrentUserId()
        if (userId.isNotBlank()) {
            remoteDataSource.delete(userId, playlistId)
        }
    }

    suspend fun addSongToPlaylist(
        playlistId: String,
        song: Song
    ) {
        if (song.isDeleted) {
            throw AppException(R.string.song_deleted)
        }

        remoteDataSource.addSong(requireCurrentUserId(), playlistId, song)
    }

    suspend fun removeSongFromPlaylist(
        playlistId: String,
        songId: String
    ) {
        remoteDataSource.removeSong(requireCurrentUserId(), playlistId, songId)
    }

    suspend fun getPlaylistSongs(playlistId: String): List<Song> {
        val userId = getCurrentUserId()
        return if (userId.isBlank()) {
            emptyList()
        } else {
            getVisibleSongs(remoteDataSource.getSongs(userId, playlistId))
        }
    }

    suspend fun getPlaylistSongs(
        ownerId: String,
        playlistId: String
    ): List<Song> {
        val finalOwnerId = ownerId.ifBlank(::getCurrentUserId)
        if (finalOwnerId.isBlank() || playlistId.isBlank()) return emptyList()

        return getVisibleSongs(remoteDataSource.getSongs(finalOwnerId, playlistId))
    }

    suspend fun getRootPlaylistSongs(playlistId: String): List<Song> {
        if (playlistId.isBlank()) return emptyList()

        return getVisibleSongs(
            firestore.collection("playlists")
                .document(playlistId)
                .collection("songs")
                .get()
                .await()
                .documents
                .mapNotNull { document ->
                    document.toObject(Song::class.java)?.copy(id = document.id)
                }
        )
    }

    suspend fun isPlaylistLiked(playlistId: String): Boolean {
        val userId = getCurrentUserId()
        return userId.isNotBlank() && remoteDataSource.isLiked(userId, playlistId)
    }

    suspend fun togglePlaylistLike(playlist: Playlist): Boolean {
        val userId = requireCurrentUserId()

        if (playlist.ownerId == userId) {
            throw AppException(R.string.cannot_like_own_playlist)
        }

        return remoteDataSource.toggleLike(userId, playlist)
    }

    suspend fun getLikedPlaylists(): List<Playlist> {
        val userId = getCurrentUserId()
        return if (userId.isBlank()) emptyList() else remoteDataSource.getLikedPlaylists(userId)
    }

    private fun getVisibleSongs(songs: List<Song>): List<Song> {
        return songs.filter { song ->
            // Accept legacy uppercase seed data while new data uses SongStatus.APPROVED.
            song.status.equals(SongStatus.APPROVED, ignoreCase = true) && !song.isDeleted
        }
    }

    private fun Playlist.ensureOwner(userId: String): Playlist {
        return if (ownerId.isBlank()) copy(ownerId = userId) else this
    }

    private fun requireCurrentUserId(): String {
        return getCurrentUserId().ifBlank {
            throw AppException(R.string.not_logged_in)
        }
    }
}
