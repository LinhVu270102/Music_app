package com.example.music_app.data.model

data class Comment(
    val id: String = "",
    val songId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val content: String = "",

    // Report / moderation
    val reportsCount: Long = 0L,
    val isDeleted: Boolean = false,
    val deletedAt: Long = 0L,
    val deletedBy: String = "",

    // Time
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)