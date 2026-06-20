package com.example.music_app.data.remote.soundcloud

import com.example.music_app.data.model.Song
import com.example.music_app.data.model.SongStatus
import com.example.music_app.data.remote.soundcloud.model.SoundCloudSurfaceTrackDto

fun SoundCloudSurfaceTrackDto.toSong(): Song {
    val finalId =
        if (id.startsWith("soundcloud_", ignoreCase = true)) {
            id
        } else {
            "soundcloud_$soundCloudId"
        }

    return Song(
        id = finalId,
        soundCloudId = soundCloudId,
        title = title,
        artist = artist,
        coverUrl = coverUrl,
        songUrl = "",
        duration = duration,
        plays = if (plays > 0L) plays else playbackCount,
        likes = if (likes > 0L) likes else likesCount,
        commentsCount = commentsCount,
        uploaderId = uploaderId.ifBlank { "soundcloud" },
        genre = genre,
        source = source.ifBlank { "soundcloud" },
        permalinkUrl = permalinkUrl,
        streamable = streamable,
        access = access,
        status = SongStatus.APPROVED
    )
}