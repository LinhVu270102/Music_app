package com.example.music_app.data.repository

import com.example.music_app.data.model.Song
import com.example.music_app.data.remote.FirebaseService
import com.example.music_app.data.remote.soundcloud.isSoundCloudSong
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MusicInteractionRepository {

    private val soundCloudRepository = SoundCloudRepository()
    private val firebaseService = FirebaseService(FirebaseFirestore.getInstance())
    private val auth = FirebaseAuth.getInstance()

    suspend fun preparePlayableSong(song: Song): Song {
        val playableSong =
            if (song.isSoundCloudSong()) {
                // SoundCloud streamUrl có thể hết hạn, nên mỗi lần phát cần lấy lại link mới.
                soundCloudRepository.getPlayableSong(song)
            } else {
                song
            }

        if (playableSong.isSoundCloudSong() && playableSong.songUrl.isNotBlank()) {
            // A cache write must not prevent playback or listening history if
            // Firestore is temporarily unavailable.
            runCatching {
                firebaseService.upsertSong(playableSong)
            }
        }

        return playableSong
    }

    suspend fun saveRecentlyPlayed(song: Song) {
        val userId = auth.currentUser?.uid ?: return

        if (song.id.isBlank() || song.isDeleted) return

        firebaseService.saveRecentlyPlayed(userId, song)
    }

    suspend fun ensureSongSaved(song: Song) {
        if (song.isSoundCloudSong() && song.id.isNotBlank()) {
            firebaseService.upsertSong(song)
        }
    }
}
