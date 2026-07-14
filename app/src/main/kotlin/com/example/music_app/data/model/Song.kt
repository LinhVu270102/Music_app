package com.example.music_app.data.model

import com.example.music_app.data.model.enums.FingerprintStatus
import com.example.music_app.data.model.enums.SongStatus
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Song(
    val id: String = "",

    // Song metadata
    val title: String = "",
    val artist: String = "",
    val coverUrl: String = "",
    val songUrl: String = "",
    val duration: Int = 0,

    // Engagement stats
    val plays: Long = 0L,
    val likes: Long = 0L,
    val commentsCount: Long = 0L,
    val reportsCount: Long = 0L,

    // Owner
    val uploaderId: String = "",

    // Discovery metadata
    val genre: String = "",
    val tags: List<String> = emptyList(),

    // Moderation
    val status: String = SongStatus.PENDING.value,
    val rejectReason: String = "",
    val reviewedBy: String = "",
    val reviewedAt: Long = 0L,

    // Audio fingerprint moderation/search state
    val fingerprintStatus: String = FingerprintStatus.PENDING.value,
    val fingerprintAlgorithm: String = "",
    val fingerprintVersion: Int = 1,
    val duplicateOfSongId: String = "",
    val duplicateScore: Double = 0.0,
    val fingerprintError: String = "",

    // Interaction permissions
    val allowComments: Boolean = true,

    // Soft delete
    val isDeleted: Boolean = false,
    val deletedAt: Long = 0L,
    val deletedBy: String = "",

    // Timestamps
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    @get:Exclude
    val statusType: SongStatus
        get() = SongStatus.from(status)

    @get:Exclude
    val fingerprintStatusType: FingerprintStatus
        get() = FingerprintStatus.from(fingerprintStatus)
}
