package com.example.music_app.core.interaction

data class ArtistFollowState(
    val userId: String,
    val followed: Boolean,
    val followerCount: Long? = null
)
