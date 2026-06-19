package com.example.music_app.data.model

data class Comment(
    val id: String = "",
    val songId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val content: String = "",
    val timelinePositionMs: Long = 0L,
    val reportsCount: Long = 0L,
    val isDeleted: Boolean = false,
    val deletedBy: String = "",
    val deletedAt: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)