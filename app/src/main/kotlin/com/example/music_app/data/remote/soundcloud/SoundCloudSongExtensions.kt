package com.example.music_app.data.remote.soundcloud

import com.example.music_app.data.model.Song

fun Song.getSoundCloudTrackId(): Long {
    if (soundCloudId > 0L) {
        return soundCloudId
    }

    if (id.startsWith("soundcloud_", ignoreCase = true)) {
        return id.substringAfter("soundcloud_").toLongOrNull() ?: 0L
    }

    return 0L
}

fun Song.isSoundCloudSong(): Boolean {
    return source.equals("soundcloud", ignoreCase = true) ||
            id.startsWith("soundcloud_", ignoreCase = true)
}

fun Song.canResolveSoundCloudStream(): Boolean {
    val trackId = getSoundCloudTrackId()

    val playableAccess =
        access.isBlank() || access.equals("playable", ignoreCase = true)

    return isSoundCloudSong() &&
            trackId > 0L &&
            streamable &&
            playableAccess
}