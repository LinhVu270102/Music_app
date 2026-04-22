package com.example.music_app.model

data class User(
    val id: String,
    val name: String,
    val email: String,
    val avatarResId: Int,      // Ảnh đại diện
    val playlists: List<Playlist>
)


