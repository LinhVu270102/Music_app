package com.example.music_app.player.state

data class ArtistFollowState(
    val userId: String,
    val followed: Boolean,
    val followerCount: Long? = null
)
