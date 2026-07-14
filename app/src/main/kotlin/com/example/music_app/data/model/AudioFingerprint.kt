package com.example.music_app.data.model

import com.example.music_app.data.model.enums.FingerprintStatus
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class AudioFingerprint(
    val songId: String = "",
    val uploaderId: String = "",
    val fingerprint: String = "",
    val fingerprintHash: String = "",
    val algorithm: String = "",
    val version: Int = 1,
    val duration: Int = 0,
    val hashBuckets: List<String> = emptyList(),
    val status: String = FingerprintStatus.PENDING.value,
    val duplicateOfSongId: String = "",
    val duplicateScore: Double = 0.0,
    val errorMessage: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    @get:Exclude
    val statusType: FingerprintStatus
        get() = FingerprintStatus.from(status)
}
