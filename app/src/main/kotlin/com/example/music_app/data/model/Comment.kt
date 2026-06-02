package com.example.music_app.data.model

data class Comment(
    val id: String = "",
    val songId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val content: String = "",
    val createdAt: Long = 0L
)