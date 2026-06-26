package com.example.music_app.data.remote

import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.utils.AppException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import com.example.music_app.data.model.SongStatus
import com.google.firebase.firestore.FieldPath

/** Low-level Firestore access for songs, moderation fields, and listening history. */
class SongRemoteDataSource(
    private val firestore: FirebaseFirestore
) {

    companion object {
        private const val RECENTLY_PLAYED_LIMIT = 20L
    }

    // =========================
    // SONG
    // =========================

    suspend fun getSongById(songId: String): Song? {
        if (songId.isBlank()) return null

        val doc = firestore.collection("songs")
            .document(songId)
            .get()
            .await()

        return doc.toObject(Song::class.java)?.copy(id = doc.id)
    }

    suspend fun getAllSongsWithIds(): List<Song> {
        val snapshot = firestore.collection("songs")
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Song::class.java)?.copy(id = doc.id)
        }.filter { song ->
            !song.isDeleted
        }
    }

    suspend fun upsertSong(song: Song) {
        if (song.id.isBlank()) {
            throw AppException(R.string.invalid_song)
        }

        firestore.collection("songs")
            .document(song.id)
            .set(song)
            .await()
    }

    suspend fun getSongsByUploaderId(userId: String): List<Song> {
        if (userId.isBlank()) return emptyList()

        val snapshot = firestore.collection("songs")
            .whereEqualTo("uploaderId", userId)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Song::class.java)?.copy(id = doc.id)
        }
    }

    suspend fun getApprovedSongsByUploaderId(userId: String): List<Song> {
        if (userId.isBlank()) return emptyList()

        val snapshot = firestore.collection("songs")
            .whereEqualTo("uploaderId", userId)
            .whereEqualTo("status", SongStatus.APPROVED)
            .whereEqualTo("isDeleted", false)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Song::class.java)?.copy(id = doc.id)
        }
    }

    suspend fun deleteSong(songId: String) {
        if (songId.isBlank()) return

        firestore.collection("songs")
            .document(songId)
            .delete()
            .await()
    }

    suspend fun getApprovedSongs(): List<Song> {
        val snapshot = firestore.collection("songs")
            .whereEqualTo("status", SongStatus.APPROVED)
            .whereEqualTo("isDeleted", false)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Song::class.java)?.copy(id = doc.id)
        }
    }

    suspend fun getLegacyApprovedSongs(): List<Song> {
        val snapshot = firestore.collection("songs")
            .whereEqualTo("status", "APPROVED")
            .whereEqualTo("isDeleted", false)
            .get()
            .await()

        return snapshot.documents.mapNotNull { document ->
            document.toObject(Song::class.java)?.copy(id = document.id)
        }
    }

    suspend fun getSongsByStatus(status: String): List<Song> {
        if (status.isBlank()) return emptyList()

        val snapshot = firestore.collection("songs")
            .whereEqualTo("status", status)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Song::class.java)?.copy(id = doc.id)
        }
    }

    suspend fun updateSongStatus(
        songId: String,
        status: String,
        reviewedBy: String,
        rejectReason: String = ""
    ) {
        if (songId.isBlank()) return

        val data = mapOf(
            "status" to status,
            "reviewedBy" to reviewedBy,
            "reviewedAt" to System.currentTimeMillis(),
            "rejectReason" to rejectReason,
            "updatedAt" to System.currentTimeMillis()
        )

        firestore.collection("songs")
            .document(songId)
            .update(data)
            .await()
    }

    suspend fun softDeleteSong(
        songId: String,
        deletedBy: String
    ) {
        if (songId.isBlank() || deletedBy.isBlank()) return

        val now = System.currentTimeMillis()

        firestore.collection("songs")
            .document(songId)
            .set(
                mapOf(
                    "isDeleted" to true,
                    "deletedAt" to now,
                    "deletedBy" to deletedBy,
                    "updatedAt" to now
                ),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun updateSongCommentPermission(
        songId: String,
        allowComments: Boolean
    ) {
        if (songId.isBlank()) return

        firestore.collection("songs")
            .document(songId)
            .set(
                mapOf(
                    "allowComments" to allowComments,
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
            .await()
    }

    // =========================
    // RECENTLY PLAYED
    // =========================

    suspend fun saveRecentlyPlayed(userId: String, song: Song) {
        if (userId.isBlank() || song.id.isBlank()) return

        val data = mapOf(
            "songId" to song.id,
            "title" to song.title,
            "artist" to song.artist,
            "coverUrl" to song.coverUrl,
            "songUrl" to song.songUrl,
            "duration" to song.duration,
            "uploaderId" to song.uploaderId,
            "genre" to song.genre,
            "status" to song.status,
            "playedAt" to System.currentTimeMillis()
        )

        firestore.collection("users")
            .document(userId)
            .collection("recentlyPlayed")
            .document(song.id)
            .set(data)
            .await()
    }

    suspend fun getRecentlyPlayedSongs(userId: String): List<Song> {
        if (userId.isBlank()) return emptyList()

        val recentlySnapshot = firestore.collection("users")
            .document(userId)
            .collection("recentlyPlayed")
            .orderBy("playedAt", Query.Direction.DESCENDING)
            .limit(RECENTLY_PLAYED_LIMIT)
            .get()
            .await()

        val songIds = recentlySnapshot.documents
            .mapNotNull { document ->
                document.getString("songId") ?: document.id
            }
            .distinct()
            .take(RECENTLY_PLAYED_LIMIT.toInt())

        if (songIds.isEmpty()) return emptyList()

        val songs = mutableListOf<Song>()

        songIds.chunked(10).forEach { ids ->
            val songSnapshot = runCatching {
                firestore.collection("songs")
                    .whereIn(FieldPath.documentId(), ids)
                    .get()
                    .await()
            }.getOrNull() ?: return@forEach

            val batchSongs = songSnapshot.documents.mapNotNull { document ->
                document.toObject(Song::class.java)?.copy(id = document.id)
            }

            songs.addAll(batchSongs)
        }

        val songMap = songs.associateBy { song -> song.id }
        val recentlyPlayedMap = recentlySnapshot.documents.associateBy { document ->
            document.getString("songId") ?: document.id
        }

        return songIds
            .mapNotNull { songId ->
                songMap[songId] ?: recentlyPlayedMap[songId]?.toRecentSong()
            }
            .filter { song ->
                song.status.equals(SongStatus.APPROVED, ignoreCase = true) && !song.isDeleted
            }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toRecentSong(): Song? {
        val songId = getString("songId") ?: id
        val title = getString("title").orEmpty()

        if (songId.isBlank() || title.isBlank()) return null

        return Song(
            id = songId,
            title = title,
            artist = getString("artist").orEmpty(),
            coverUrl = getString("coverUrl").orEmpty(),
            songUrl = getString("songUrl").orEmpty(),
            duration = (getLong("duration") ?: 0L).toInt(),
            uploaderId = getString("uploaderId").orEmpty(),
            genre = getString("genre").orEmpty(),
            status = getString("status") ?: SongStatus.APPROVED
        )
    }

    suspend fun resubmitSongForReview(songId: String) {
        if (songId.isBlank()) return

        val now = System.currentTimeMillis()
        firestore.collection("songs")
            .document(songId)
            .set(
                mapOf(
                    "status" to SongStatus.PENDING,
                    "rejectReason" to "",
                    "reviewedBy" to "",
                    "reviewedAt" to 0L,
                    "updatedAt" to now
                ),
                SetOptions.merge()
            )
            .await()
    }

}
