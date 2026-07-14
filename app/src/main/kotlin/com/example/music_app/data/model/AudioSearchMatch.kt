package com.example.music_app.data.model

data class AudioSearchMatch(
    val songId: String = "",
    val matchType: String = "",
    val score: Double = 0.0,
    val song: Song? = null
)
