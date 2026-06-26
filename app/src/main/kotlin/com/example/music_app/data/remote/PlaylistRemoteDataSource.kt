package com.example.music_app.data.remote

import android.util.Log
import com.example.music_app.R
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Song
import com.example.music_app.utils.AppException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/** Low-level Firestore operations for user playlists and saved public playlists. */
class PlaylistRemoteDataSource(
    private val firestore: FirebaseFirestore
) {

    suspend fun create(userId: String, name: String, description: String = ""): Playlist {
        if (userId.isBlank()) throw AppException(R.string.invalid_user)
        if (name.isBlank()) throw AppException(R.string.playlist_name_empty)

        val playlistRef = firestore.collection("users")
            .document(userId)
            .collection("playlists")
            .document()
        val publicPlaylistRef = firestore.collection("playlists").document(playlistRef.id)
        val now = System.currentTimeMillis()
        val playlist = Playlist(
            id = playlistRef.id,
            name = name,
            description = description,
            ownerId = userId,
            isPublic = true,
            createdAt = now,
            updatedAt = now
        )

        firestore.batch()
            .set(playlistRef, playlist)
            // Public mirror is the searchable index; private data remains under the owner.
            .set(publicPlaylistRef, playlist)
            .commit()
            .await()

        return playlist
    }

    suspend fun getUserPlaylists(userId: String): List<Playlist> {
        if (userId.isBlank()) return emptyList()

        return firestore.collection("users")
            .document(userId)
            .collection("playlists")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
            .documents
            .mapNotNull(::toPlaylist)
    }

    suspend fun getPublicUserPlaylists(userId: String): List<Playlist> {
        if (userId.isBlank()) return emptyList()

        return firestore.collection("users")
            .document(userId)
            .collection("playlists")
            .whereEqualTo("isPublic", true)
            .get()
            .await()
            .documents
            .mapNotNull(::toPlaylist)
            .sortedByDescending(Playlist::updatedAt)
    }

    suspend fun isLiked(userId: String, playlistId: String): Boolean {
        if (userId.isBlank() || playlistId.isBlank()) return false

        return firestore.collection("users")
            .document(userId)
            .collection("likedPlaylists")
            .document(playlistId)
            .get()
            .await()
            .exists()
    }

    suspend fun toggleLike(userId: String, playlist: Playlist): Boolean {
        if (userId.isBlank() || playlist.id.isBlank()) return false

        val likedPlaylistRef = firestore.collection("users")
            .document(userId)
            .collection("likedPlaylists")
            .document(playlist.id)

        if (likedPlaylistRef.get().await().exists()) {
            likedPlaylistRef.delete().await()
            return false
        }

        likedPlaylistRef.set(playlist.copy(updatedAt = System.currentTimeMillis())).await()
        return true
    }

    suspend fun getLikedPlaylists(userId: String): List<Playlist> {
        if (userId.isBlank()) return emptyList()

        return firestore.collection("users")
            .document(userId)
            .collection("likedPlaylists")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .get()
            .await()
            .documents
            .mapNotNull(::toPlaylist)
    }

    suspend fun saveRecentlyPlayedPlaylist(userId: String, playlist: Playlist) {
        if (userId.isBlank() || playlist.id.isBlank() || playlist.name.isBlank()) return

        val now = System.currentTimeMillis()
        val data = mapOf(
            "playlistId" to playlist.id,
            "name" to playlist.name,
            "description" to playlist.description,
            "coverUrl" to playlist.coverUrl,
            "ownerId" to playlist.ownerId,
            "isPublic" to playlist.isPublic,
            "songsCount" to playlist.songsCount,
            "createdAt" to playlist.createdAt,
            "updatedAt" to now,
            "playedAt" to now
        )

        firestore.collection("users")
            .document(userId)
            .collection("recentlyPlayedPlaylists")
            .document(playlist.id)
            .set(data)
            .await()
    }

    suspend fun getRecentlyPlayedPlaylists(userId: String): List<Playlist> {
        if (userId.isBlank()) return emptyList()

        return firestore.collection("users")
            .document(userId)
            .collection("recentlyPlayedPlaylists")
            .orderBy("playedAt", Query.Direction.DESCENDING)
            .limit(RECENTLY_PLAYED_PLAYLIST_LIMIT)
            .get()
            .await()
            .documents
            .mapNotNull(::toRecentlyPlayedPlaylist)
    }

    suspend fun delete(userId: String, playlistId: String) {
        if (userId.isBlank() || playlistId.isBlank()) return

        val userPlaylistRef = userPlaylist(userId, playlistId)
        val userSongDocs = userPlaylistRef.collection("songs").get().await().documents

        firestore.batch().apply {
            userSongDocs.forEach { document -> delete(document.reference) }
            delete(userPlaylistRef)
        }.commit().await()

        deletePublicMirrorSafely(playlistId)
    }

    suspend fun addSong(userId: String, playlistId: String, song: Song) {
        if (userId.isBlank() || playlistId.isBlank() || song.id.isBlank()) return

        val firstSongCoverUrl = firstSongCoverUrlIfPlaylistNeedsCover(
            userId = userId,
            playlistId = playlistId,
            song = song
        )
        val songRef = playlistSongs(userId, playlistId).document(song.id)
        if (songRef.get().await().exists()) return

        songRef.set(song.toPlaylistSongData()).await()
        updateUserSongCountSafely(
            userId = userId,
            playlistId = playlistId,
            delta = 1,
            coverUrl = firstSongCoverUrl
        )
        syncAddedSongToPublicMirrorSafely(
            userId = userId,
            playlistId = playlistId,
            song = song,
            coverUrl = firstSongCoverUrl
        )
    }

    suspend fun removeSong(userId: String, playlistId: String, songId: String) {
        if (userId.isBlank() || playlistId.isBlank() || songId.isBlank()) return

        val songRef = playlistSongs(userId, playlistId).document(songId)
        if (!songRef.get().await().exists()) return

        songRef.delete().await()
        updateUserSongCountSafely(userId, playlistId, -1)
        syncRemovedSongFromPublicMirrorSafely(userId, playlistId, songId)
    }

    suspend fun getSongs(userId: String, playlistId: String): List<Song> {
        if (userId.isBlank() || playlistId.isBlank()) return emptyList()

        return playlistSongs(userId, playlistId)
            .orderBy("addedAt", Query.Direction.DESCENDING)
            .get()
            .await()
            .documents
            .mapNotNull { document ->
                document.toObject(Song::class.java)?.copy(id = document.id)
            }
    }

    private suspend fun updateUserSongCountSafely(
        userId: String,
        playlistId: String,
        delta: Long,
        coverUrl: String = ""
    ) {
        val now = System.currentTimeMillis()

        runCatching {
            val updates = mutableMapOf<String, Any>(
                "songsCount" to FieldValue.increment(delta),
                "updatedAt" to now
            )

            if (coverUrl.isNotBlank()) {
                updates["coverUrl"] = coverUrl
            }

            userPlaylist(userId, playlistId).update(
                updates
            ).await()
        }.onFailure { error ->
            Log.w(
                TAG,
                "Could not update user playlist count for playlistId=$playlistId",
                error
            )
        }
    }

    private suspend fun syncAddedSongToPublicMirrorSafely(
        userId: String,
        playlistId: String,
        song: Song,
        coverUrl: String = ""
    ) {
        val now = System.currentTimeMillis()

        runCatching {
            val playlistUpdates = mutableMapOf<String, Any>(
                "ownerId" to userId,
                "songsCount" to FieldValue.increment(1),
                "updatedAt" to now
            )

            if (coverUrl.isNotBlank()) {
                playlistUpdates["coverUrl"] = coverUrl
            }

            firestore.batch().apply {
                set(
                    rootPlaylist(playlistId).collection("songs").document(song.id),
                    song.toPlaylistSongData()
                )
                set(
                    rootPlaylist(playlistId),
                    playlistUpdates,
                    SetOptions.merge()
                )
            }.commit().await()
        }.onFailure { error ->
            Log.w(
                TAG,
                "Could not sync added song to public playlist mirror playlistId=$playlistId",
                error
            )
        }
    }

    private suspend fun syncRemovedSongFromPublicMirrorSafely(
        userId: String,
        playlistId: String,
        songId: String
    ) {
        val now = System.currentTimeMillis()

        runCatching {
            firestore.batch().apply {
                delete(rootPlaylist(playlistId).collection("songs").document(songId))
                set(
                    rootPlaylist(playlistId),
                    mapOf(
                        "ownerId" to userId,
                        "songsCount" to FieldValue.increment(-1),
                        "updatedAt" to now
                    ),
                    SetOptions.merge()
                )
            }.commit().await()
        }.onFailure { error ->
            Log.w(
                TAG,
                "Could not sync removed song from public playlist mirror playlistId=$playlistId",
                error
            )
        }
    }

    private suspend fun deletePublicMirrorSafely(playlistId: String) {
        runCatching {
            val rootPlaylistRef = rootPlaylist(playlistId)
            val rootSongDocs = rootPlaylistRef.collection("songs").get().await().documents

            firestore.batch().apply {
                rootSongDocs.forEach { document -> delete(document.reference) }
                delete(rootPlaylistRef)
            }.commit().await()
        }.onFailure { error ->
            Log.w(
                TAG,
                "Could not delete public playlist mirror playlistId=$playlistId",
                error
            )
        }
    }

    private suspend fun firstSongCoverUrlIfPlaylistNeedsCover(
        userId: String,
        playlistId: String,
        song: Song
    ): String {
        if (song.coverUrl.isBlank()) return ""

        return runCatching {
            val playlistSnapshot = userPlaylist(userId, playlistId).get().await()
            val currentCoverUrl = playlistSnapshot.getString("coverUrl").orEmpty()

            if (currentCoverUrl.isNotBlank()) return@runCatching ""

            val hasExistingSongs = playlistSongs(userId, playlistId)
                .limit(1)
                .get()
                .await()
                .documents
                .isNotEmpty()

            if (hasExistingSongs) "" else song.coverUrl
        }.getOrDefault("")
    }

    private fun userPlaylist(userId: String, playlistId: String) = firestore.collection("users")
        .document(userId)
        .collection("playlists")
        .document(playlistId)

    private fun rootPlaylist(playlistId: String) = firestore.collection("playlists")
        .document(playlistId)

    private fun playlistSongs(userId: String, playlistId: String) = userPlaylist(userId, playlistId)
        .collection("songs")

    private fun toPlaylist(document: com.google.firebase.firestore.DocumentSnapshot): Playlist? {
        return document.toObject(Playlist::class.java)?.copy(id = document.id)
    }

    private fun toRecentlyPlayedPlaylist(
        document: com.google.firebase.firestore.DocumentSnapshot
    ): Playlist? {
        val playlistId = document.getString("playlistId") ?: document.id
        val name = document.getString("name").orEmpty()
        if (playlistId.isBlank() || name.isBlank()) return null

        val playedAt = document.getLong("playedAt") ?: document.getLong("updatedAt") ?: 0L

        return Playlist(
            id = playlistId,
            name = name,
            description = document.getString("description").orEmpty(),
            coverUrl = document.getString("coverUrl").orEmpty(),
            ownerId = document.getString("ownerId").orEmpty(),
            isPublic = document.getBoolean("isPublic") ?: true,
            songsCount = document.getLong("songsCount") ?: 0L,
            createdAt = document.getLong("createdAt") ?: 0L,
            updatedAt = playedAt
        )
    }

    private fun Song.toPlaylistSongData(): Map<String, Any> {
        return mapOf(
            "songId" to id,
            "title" to title,
            "artist" to artist,
            "coverUrl" to coverUrl,
            "songUrl" to songUrl,
            "duration" to duration,
            "uploaderId" to uploaderId,
            "genre" to genre,
            "tags" to tags,
            "status" to status,
            "addedAt" to System.currentTimeMillis()
        )
    }

    private companion object {
        const val TAG = "PlaylistRemoteDataSource"
        const val RECENTLY_PLAYED_PLAYLIST_LIMIT = 20L
    }
}
