package com.example.music_app.data.model

import android.net.Uri

data class UploadMusicRequest(
    val audioUri: Uri,
    val coverUri: Uri?,
    val audioExtension: String,
    val coverExtension: String?,
    val title: String,
    val artist: String,
    val genre: String,
    val tags: List<String>,
    val unknownArtist: String
)
