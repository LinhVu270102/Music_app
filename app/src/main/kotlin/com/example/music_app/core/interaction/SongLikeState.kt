package com.example.music_app.core.interaction

data class SongLikeState(
    val songId: String,
    val liked: Boolean,
    val likesCount: Long? = null,
    val commentsCount: Long? = null,
    val changedByUser: Boolean = false
)
