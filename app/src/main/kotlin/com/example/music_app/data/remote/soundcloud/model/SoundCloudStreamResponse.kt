package com.example.music_app.data.remote.soundcloud.model

data class SoundCloudStreamResponse(
    val streamUrl: String = "",
    val protocol: String = "",
    val mimeType: String = "",
    val duration: Int = 0,
    val access: String = "",
    val streamable: Boolean = false
)