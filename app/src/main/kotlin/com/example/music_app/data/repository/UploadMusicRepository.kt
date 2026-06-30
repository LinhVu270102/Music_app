package com.example.music_app.data.repository

import android.net.Uri
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.UploadMusicRequest
import com.example.music_app.data.model.enums.SongStatus
import com.example.music_app.utils.AppException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/** Handles the Firebase Storage and Firestore work for a user music upload. */
class UploadMusicRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {

    // Public data operation
    suspend fun uploadMusic(request: UploadMusicRequest): Song {
        val user = auth.currentUser ?: throw AppException(R.string.not_logged_in)
        val songId = firestore.collection("songs").document().id

        val audioUrl = uploadFile(
            uri = request.audioUri,
            path = "songs/${user.uid}/$songId.${request.audioExtension}"
        )

        val coverUrl = request.coverUri?.let { coverUri ->
            uploadFile(
                uri = coverUri,
                path = "covers/${user.uid}/$songId.${request.coverExtension ?: "jpg"}"
            )
        }.orEmpty()

        val now = System.currentTimeMillis()
        val song = Song(
            id = songId,
            title = request.title,
            artist = request.artist.ifBlank {
                user.displayName ?: user.email ?: request.unknownArtist
            },
            coverUrl = coverUrl,
            songUrl = audioUrl,
            uploaderId = user.uid,
            genre = request.genre,
            tags = request.tags,
            status = SongStatus.PENDING.value,
            createdAt = now,
            updatedAt = now
        )

        firestore.collection("songs")
            .document(songId)
            .set(song)
            .await()

        return song
    }

    private suspend fun uploadFile(
        uri: Uri,
        path: String
    ): String {
        val reference = storage.reference.child(path)
        reference.putFile(uri).await()
        return reference.downloadUrl.await().toString()
    }
}
