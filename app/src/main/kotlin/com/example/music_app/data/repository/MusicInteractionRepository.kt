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

        if (playableSong.songUrl.isNotBlank()) {
            firebaseService.upsertSong(playableSong)
        }

        return playableSong
    }

    suspend fun preparePlayableSongAndSaveRecently(song: Song): Song {
        val playableSong = preparePlayableSong(song)

        val userId = auth.currentUser?.uid

        if (
            userId != null &&
            playableSong.id.isNotBlank() &&
            playableSong.songUrl.isNotBlank()
        ) {
            firebaseService.saveRecentlyPlayed(
                userId = userId,
                songId = playableSong.id
            )
        }

        return playableSong
    }

    suspend fun ensureSongSaved(song: Song) {
        if (song.id.isNotBlank()) {
            firebaseService.upsertSong(song)
        }
    }
}