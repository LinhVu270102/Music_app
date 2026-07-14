package com.example.music_app.data.firebase.firestore

import com.example.music_app.R
import com.example.music_app.data.model.AudioFingerprint
import com.example.music_app.data.model.enums.FingerprintStatus
import com.example.music_app.utils.AppException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/** Low-level Firestore access for generated audio fingerprints and duplicate detection results. */
class AudioFingerprintFirestoreDataSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun getBySongId(songId: String): AudioFingerprint? {
        if (songId.isBlank()) return null

        return audioFingerprints()
            .document(songId)
            .get()
            .await()
            .toAudioFingerprint()
    }

    suspend fun getByStatus(status: FingerprintStatus): List<AudioFingerprint> {
        return audioFingerprints()
            .whereEqualTo("status", status.value)
            .get()
            .await()
            .documents
            .mapNotNull { document -> document.toAudioFingerprint() }
            .sortedByDescending { fingerprint -> fingerprint.updatedAt }
    }

    suspend fun upsert(fingerprint: AudioFingerprint) {
        if (fingerprint.songId.isBlank()) {
            throw AppException(R.string.invalid_song)
        }

        audioFingerprints()
            .document(fingerprint.songId)
            .set(fingerprint.withWriteTimestamps(), SetOptions.merge())
            .await()
    }

    suspend fun markProcessing(songId: String) {
        if (songId.isBlank()) return

        updateStatus(
            songId = songId,
            status = FingerprintStatus.PROCESSING
        )
    }

    suspend fun markFailed(
        songId: String,
        errorMessage: String
    ) {
        if (songId.isBlank()) return

        updateStatus(
            songId = songId,
            status = FingerprintStatus.FAILED,
            extraData = mapOf("errorMessage" to errorMessage)
        )
    }

    suspend fun updateStatus(
        songId: String,
        status: FingerprintStatus,
        extraData: Map<String, Any> = emptyMap()
    ) {
        if (songId.isBlank()) return

        audioFingerprints()
            .document(songId)
            .set(
                mapOf(
                    "songId" to songId,
                    "status" to status.value,
                    "updatedAt" to System.currentTimeMillis()
                ) + extraData,
                SetOptions.merge()
            )
            .await()
    }

    private fun audioFingerprints() = firestore.collection(COLLECTION_AUDIO_FINGERPRINTS)

    private fun AudioFingerprint.withWriteTimestamps(): AudioFingerprint {
        val now = System.currentTimeMillis()

        return copy(
            createdAt = createdAt.takeIf { it > 0L } ?: now,
            updatedAt = now
        )
    }

    private fun DocumentSnapshot.toAudioFingerprint(): AudioFingerprint? {
        return toObject(AudioFingerprint::class.java)?.copy(songId = id)
    }

    private companion object {
        const val COLLECTION_AUDIO_FINGERPRINTS = "audioFingerprints"
    }
}
