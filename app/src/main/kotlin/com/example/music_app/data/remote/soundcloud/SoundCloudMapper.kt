package com.example.music_app.data.remote.soundcloud

import com.example.music_app.data.model.Song
import com.example.music_app.data.model.SongStatus
import com.example.music_app.data.remote.soundcloud.model.SoundCloudTrackDto

fun SoundCloudTrackDto.toSong(): Song {
    val finalSoundCloudId = resolveSoundCloudId()
    val finalId = "soundcloud_$finalSoundCloudId"

    val now = System.currentTimeMillis()

    val canResolveStream =
        finalSoundCloudId > 0L &&
                streamable &&
                permalinkUrl.isNotBlank() &&
                access.isPlayableAccess()

    return Song(
        id = finalId,
        title = title.trim(),
        artist = artist.trim(),
        coverUrl = coverUrl,
        songUrl = "",
        duration = duration,

        plays = playbackCount,
        likes = likesCount,
        commentsCount = 0L,

        uploaderId = "soundcloud",
        genre = genre,
        tags = listOf("online", "soundcloud"),

        status = if (canResolveStream) {
            SongStatus.APPROVED
        } else {
            SongStatus.PENDING
        },
        rejectReason = "",
        reviewedBy = "system",
        reviewedAt = now,
        createdAt = now,
        updatedAt = now,

        source = "soundcloud",
        soundCloudId = finalSoundCloudId,
        permalinkUrl = permalinkUrl,
        streamable = streamable,
        access = access
    )
}

private fun SoundCloudTrackDto.resolveSoundCloudId(): Long {
    if (soundCloudId > 0L) {
        return soundCloudId
    }

    if (id.startsWith("soundcloud_", ignoreCase = true)) {
        return id.substringAfter("soundcloud_").toLongOrNull() ?: 0L
    }

    return id.toLongOrNull() ?: 0L
}

private fun String.isPlayableAccess(): Boolean {
    return isBlank() || equals("playable", ignoreCase = true)
}