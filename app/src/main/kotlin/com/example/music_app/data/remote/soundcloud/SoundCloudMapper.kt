package com.example.music_app.data.remote.soundcloud

import com.example.music_app.data.model.Song
import com.example.music_app.data.model.SongStatus

fun SoundCloudTrackDto.toSong(): Song {
    return Song(
        id = id,
        title = title,
        artist = artist,
        coverUrl = coverUrl,
        songUrl = "",
        duration = duration,
        plays = playbackCount,
        likes = likesCount,
        commentsCount = 0L,
        uploaderId = "soundcloud_$soundCloudId",
        genre = genre,
        tags = listOf("online", "soundcloud"),
        status = SongStatus.APPROVED,
        rejectReason = "",
        reviewedBy = "system",
        reviewedAt = System.currentTimeMillis(),
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}

fun Song.getSoundCloudTrackId(): Long {
    return id.removePrefix("soundcloud_").toLongOrNull() ?: 0L
}

fun Song.isSoundCloudSong(): Boolean {
    return id.startsWith("soundcloud_")
}