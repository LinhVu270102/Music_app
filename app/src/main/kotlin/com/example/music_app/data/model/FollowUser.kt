package com.example.music_app.data.model

data class FollowUser(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val followedAt: Long = 0L
)