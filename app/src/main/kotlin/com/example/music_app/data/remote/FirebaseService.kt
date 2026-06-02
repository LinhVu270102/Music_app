package com.example.music_app.data.remote

import com.example.music_app.data.model.Song
import com.example.music_app.data.model.User
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class FirebaseService(
    private val firestore: FirebaseFirestore
) {

    suspend fun getSongById(songId: String): Song? {
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
        }
    }

    suspend fun upsertSong(song: Song) {
        firestore.collection("songs")
            .document(song.id)
            .set(song)
            .await()
    }

    suspend fun saveRecentlyPlayed(userId: String, songId: String) {
        if (userId.isBlank() || songId.isBlank()) return

        val data = mapOf(
            "songId" to songId,
            "playedAt" to System.currentTimeMillis()
        )

        firestore.collection("users")
            .document(userId)
            .collection("recentlyPlayed")
            .document(songId)
            .set(data)
            .await()
    }

    suspend fun getRecentlyPlayedSongs(
        userId: String,
        limit: Long = 4
    ): List<Song> {
        if (userId.isBlank()) return emptyList()

        val snapshot = firestore.collection("users")
            .document(userId)
            .collection("recentlyPlayed")
            .orderBy("playedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()

        val songIds = snapshot.documents.mapNotNull { doc ->
            doc.getString("songId")
        }

        return songIds.mapNotNull { songId ->
            getSongById(songId)
        }
    }

    suspend fun getUserById(userId: String): User? {
        if (userId.isBlank()) return null

        val doc = firestore.collection("users")
            .document(userId)
            .get()
            .await()

        return doc.toObject(User::class.java)?.copy(uid = doc.id)
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

    suspend fun likeSong(userId: String, songId: String) {
        if (userId.isBlank() || songId.isBlank()) return

        val likedRef = firestore.collection("users")
            .document(userId)
            .collection("likedSongs")
            .document(songId)

        val songRef = firestore.collection("songs")
            .document(songId)

        val likedDoc = likedRef.get().await()

        if (likedDoc.exists()) return

        val data = mapOf(
            "songId" to songId,
            "likedAt" to System.currentTimeMillis()
        )

        likedRef.set(data).await()

        songRef.update("likes", FieldValue.increment(1)).await()
    }

    suspend fun unlikeSong(userId: String, songId: String) {
        if (userId.isBlank() || songId.isBlank()) return

        val likedRef = firestore.collection("users")
            .document(userId)
            .collection("likedSongs")
            .document(songId)

        val songRef = firestore.collection("songs")
            .document(songId)

        val likedDoc = likedRef.get().await()

        if (!likedDoc.exists()) return

        likedRef.delete().await()

        songRef.update("likes", FieldValue.increment(-1)).await()
    }

    suspend fun isSongLiked(userId: String, songId: String): Boolean {
        if (userId.isBlank() || songId.isBlank()) return false

        val doc = firestore.collection("users")
            .document(userId)
            .collection("likedSongs")
            .document(songId)
            .get()
            .await()

        return doc.exists()
    }

    suspend fun getLikedSongs(userId: String): List<Song> {
        if (userId.isBlank()) return emptyList()

        val snapshot = firestore.collection("users")
            .document(userId)
            .collection("likedSongs")
            .orderBy("likedAt", Query.Direction.DESCENDING)
            .get()
            .await()

        val songIds = snapshot.documents.mapNotNull { doc ->
            doc.getString("songId")
        }

        return songIds.mapNotNull { songId ->
            getSongById(songId)
        }
    }
}