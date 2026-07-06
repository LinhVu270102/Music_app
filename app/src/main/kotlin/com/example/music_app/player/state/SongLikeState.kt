package com.example.music_app.player.state

data class SongLikeState(
    val songId: String,
    val liked: Boolean,
    val likesCount: Long? = null,
    val commentsCount: Long? = null,
    val changedByUser: Boolean = false
)
