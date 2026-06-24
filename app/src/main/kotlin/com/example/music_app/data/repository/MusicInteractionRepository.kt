package com.example.music_app.data.repository

import com.example.music_app.data.model.Song
import com.example.music_app.data.remote.SongRemoteDataSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** Persists local player interactions for the Firebase-backed catalog. */
class MusicInteractionRepository {

    private val songRemoteDataSource = SongRemoteDataSource(FirebaseFirestore.getInstance())
    private val auth = FirebaseAuth.getInstance()

    suspend fun preparePlayableSong(song: Song): Song = song

    suspend fun saveRecentlyPlayed(song: Song) {
        val userId = auth.currentUser?.uid ?: return
        if (song.id.isBlank() || song.isDeleted) return

        songRemoteDataSource.saveRecentlyPlayed(userId, song)
    }

    suspend fun ensureSongSaved(song: Song) {
        if (song.id.isNotBlank()) songRemoteDataSource.upsertSong(song)
    }
}
