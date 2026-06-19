package com.example.music_app.data.remote.soundcloud.model

data class SoundCloudTrackDto(
    val id: String = "",
    val soundCloudId: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val coverUrl: String = "",
    val duration: Int = 0,
    val genre: String = "",
    val permalinkUrl: String = "",
    val playbackCount: Long = 0L,
    val likesCount: Long = 0L,
    val streamable: Boolean = false,
    val access: String = ""
)