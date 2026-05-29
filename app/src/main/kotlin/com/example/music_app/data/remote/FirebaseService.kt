package com.example.music_app.data.remote

import com.example.music_app.data.model.Song
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
}