package com.example.music_app.data.repository

import com.example.music_app.data.model.Song
import com.example.music_app.data.remote.FirebaseService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.music_app.data.model.User

class SongRepository {

    private val firebaseService = FirebaseService(FirebaseFirestore.getInstance())
    private val auth = FirebaseAuth.getInstance()

    suspend fun getSong(songId: String): Song? {
        return firebaseService.getSongById(songId)
    }

    suspend fun getAllSongs(): List<Song> {
        return firebaseService.getAllSongsWithIds()
    }

    suspend fun upsertSong(song: Song) {
        firebaseService.upsertSong(song)
    }

    suspend fun saveRecentlyPlayed(song: Song) {
        val userId = auth.currentUser?.uid ?: return
        firebaseService.saveRecentlyPlayed(userId, song.id)
    }

    suspend fun getRecentlyPlayedSongs(): List<Song> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return firebaseService.getRecentlyPlayedSongs(userId)
    }
    suspend fun getCurrentUserProfile(): User? {
        val userId = auth.currentUser?.uid ?: return null
        return firebaseService.getUserById(userId)
    }

    suspend fun getMyUploadedSongs(): List<Song> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return firebaseService.getSongsByUploaderId(userId)
    }
}