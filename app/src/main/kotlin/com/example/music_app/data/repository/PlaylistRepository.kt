package com.example.music_app.data.repository

import com.example.music_app.R
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.enums.SongStatus
import com.example.music_app.data.remote.PlaylistRemoteDataSource
import com.example.music_app.utils.AppException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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
        val normalizedName = name.trim()

        if (normalizedName.isBlank()) {
            throw AppException(R.string.playlist_name_empty)
        }

        return remoteDataSource.create(
            userId = userId,
            name = normalizedName,
            description = description.trim()
        )
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
        val userId = requireCurrentUserId()
        validatePlaylistSong(playlistId, song)

        val coverUrl = remoteDataSource.firstSongCoverUrlIfPlaylistNeedsCover(
            userId = userId,
            playlistId = playlistId,
            song = song
        )
        val wasAdded = remoteDataSource.addUserSong(
            userId = userId,
            playlistId = playlistId,
            song = song
        )

        if (!wasAdded) return

        remoteDataSource.updateUserSongCountSafely(
            userId = userId,
            playlistId = playlistId,
            delta = 1,
            coverUrl = coverUrl
        )
        remoteDataSource.syncAddedSongToPublicMirrorSafely(
            userId = userId,
            playlistId = playlistId,
            song = song,
            coverUrl = coverUrl
        )
    }

    suspend fun removeSongFromPlaylist(
        playlistId: String,
        songId: String
    ) {
        val userId = requireCurrentUserId()

        if (playlistId.isBlank() || songId.isBlank()) return

        val wasRemoved = remoteDataSource.removeUserSong(
            userId = userId,
            playlistId = playlistId,
            songId = songId
        )

        if (!wasRemoved) return

        remoteDataSource.updateUserSongCountSafely(
            userId = userId,
            playlistId = playlistId,
            delta = -1
        )
        remoteDataSource.syncRemovedSongFromPublicMirrorSafely(
            userId = userId,
            playlistId = playlistId,
            songId = songId
        )
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

        return getVisibleSongs(remoteDataSource.getRootSongs(playlistId))
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
            song.statusType == SongStatus.APPROVED && !song.isDeleted
        }
    }

    private fun validatePlaylistSong(
        playlistId: String,
        song: Song
    ) {
        if (playlistId.isBlank() || song.id.isBlank()) {
            throw AppException(R.string.invalid_song)
        }

        if (song.isDeleted) {
            throw AppException(R.string.song_deleted)
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
