package com.example.music_app.data.remote.soundcloud.model

data class SoundCloudSearchResponse(
    val results: List<SoundCloudTrackDto> = emptyList()
)