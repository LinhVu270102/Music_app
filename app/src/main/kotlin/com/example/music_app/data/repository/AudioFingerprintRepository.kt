package com.example.music_app.data.repository

import com.example.music_app.data.firebase.firestore.AudioFingerprintFirestoreDataSource
import com.example.music_app.data.firebase.firestore.SongFirestoreDataSource
import com.example.music_app.data.model.AudioFingerprint
import com.example.music_app.data.model.enums.FingerprintStatus
import com.google.firebase.firestore.FirebaseFirestore

/** Coordinates fingerprint documents with the summary fields stored on songs. */
class AudioFingerprintRepository(
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val fingerprintFirestoreDataSource: AudioFingerprintFirestoreDataSource =
        AudioFingerprintFirestoreDataSource(firestore),
    private val songFirestoreDataSource: SongFirestoreDataSource =
        SongFirestoreDataSource(firestore)
) {

    suspend fun getBySongId(songId: String): AudioFingerprint? {
        return fingerprintFirestoreDataSource.getBySongId(songId)
    }

    suspend fun markProcessing(songId: String) {
        fingerprintFirestoreDataSource.markProcessing(songId)
        songFirestoreDataSource.updateFingerprintSummary(
            songId = songId,
            status = FingerprintStatus.PROCESSING
        )
    }

    suspend fun saveUniqueResult(fingerprint: AudioFingerprint) {
        val result = fingerprint.copy(
            status = FingerprintStatus.UNIQUE.value,
            duplicateOfSongId = "",
            duplicateScore = 0.0,
            errorMessage = ""
        )

        fingerprintFirestoreDataSource.upsert(result)
        songFirestoreDataSource.updateFingerprintSummary(
            songId = result.songId,
            status = FingerprintStatus.UNIQUE,
            algorithm = result.algorithm,
            version = result.version
        )
    }

    suspend fun saveDuplicateResult(fingerprint: AudioFingerprint) {
        val result = fingerprint.copy(
            status = FingerprintStatus.DUPLICATE.value,
            errorMessage = ""
        )

        fingerprintFirestoreDataSource.upsert(result)
        songFirestoreDataSource.updateFingerprintSummary(
            songId = result.songId,
            status = FingerprintStatus.DUPLICATE,
            algorithm = result.algorithm,
            version = result.version,
            duplicateOfSongId = result.duplicateOfSongId,
            duplicateScore = result.duplicateScore
        )
    }

    suspend fun markFailed(
        songId: String,
        errorMessage: String
    ) {
        fingerprintFirestoreDataSource.markFailed(
            songId = songId,
            errorMessage = errorMessage
        )
        songFirestoreDataSource.updateFingerprintSummary(
            songId = songId,
            status = FingerprintStatus.FAILED,
            errorMessage = errorMessage
        )
    }
}
