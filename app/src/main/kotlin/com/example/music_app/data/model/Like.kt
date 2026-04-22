package com.example.music_app.model

data class Like(
    val id: String,          // ID duy nhất cho lượt like
    val userId: String,      // ID người dùng đã like
    val targetId: String,    // ID đối tượng được like (Song, Playlist, Comment...)
    val targetType: String,  // Loại đối tượng: "song", "playlist", "comment"
    val timestamp: Long      // Thời gian like (epoch millis)
)
