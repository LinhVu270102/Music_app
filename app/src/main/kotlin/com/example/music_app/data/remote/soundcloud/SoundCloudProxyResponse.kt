package com.example.music_app.data.remote.soundcloud

data class SoundCloudSearchResponse(
    val results: List<SoundCloudTrackDto> = emptyList()
)

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

data class SoundCloudStreamResponse(
    val streamUrl: String = "",
    val protocol: String = "",
    val mimeType: String = "",
    val duration: Int = 0,
    val access: String = "",
    val streamable: Boolean = false
)