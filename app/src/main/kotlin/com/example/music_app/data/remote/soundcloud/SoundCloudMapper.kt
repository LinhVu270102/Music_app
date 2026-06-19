package com.example.music_app.data.remote.soundcloud

import com.example.music_app.data.model.Song
import com.example.music_app.data.model.SongStatus

fun SoundCloudTrackDto.toSong(): Song {
    val finalId = if (id.startsWith("soundcloud_")) {
        id
    } else {
        "soundcloud_$soundCloudId"
    }

    return Song(
        id = finalId,
        title = title,
        artist = artist,
        coverUrl = coverUrl,
        songUrl = "",
        duration = duration,

        plays = playbackCount,
        likes = likesCount,
        commentsCount = 0L,

        uploaderId = "soundcloud",
        genre = genre,
        tags = listOf("online", "soundcloud"),

        status = SongStatus.APPROVED,
        rejectReason = "",
        reviewedBy = "system",
        reviewedAt = System.currentTimeMillis(),
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),

        source = "soundcloud",
        soundCloudId = soundCloudId,
        permalinkUrl = permalinkUrl,
        streamable = streamable,
        access = access
    )
}

fun Song.getSoundCloudTrackId(): Long {
    if (soundCloudId > 0L) {
        return soundCloudId
    }

    if (id.startsWith("soundcloud_")) {
        return id.removePrefix("soundcloud_").toLongOrNull() ?: 0L
    }

    return 0L
}

fun Song.isSoundCloudSong(): Boolean {
    return source == "soundcloud" || id.startsWith("soundcloud_")
}