package com.example.music_app.data.repository

import com.example.music_app.data.model.Song
import com.example.music_app.data.remote.FirebaseService
import com.google.firebase.firestore.FirebaseFirestore

class SongRepository {

    private val firebaseService = FirebaseService(FirebaseFirestore.getInstance())

    suspend fun getSong(songId: String): Song? {
        return firebaseService.getSongById(songId)
    }

    suspend fun getAllSongs(): List<Song> {
        return firebaseService.getAllSongsWithIds()
    }

    suspend fun upsertSong(song: Song) {
        firebaseService.upsertSong(song)
    }
}