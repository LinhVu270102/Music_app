package com.example.music_app.data.model

data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val bio: String = "",

    // Role
    val role: String = "listener",
    val accountStatus: String = "active",

    // Time
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastLoginAt: Long = 0L,

    // Stats
    val likedSongsCount: Long = 0L,
    val playlistsCount: Long = 0L,
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    val uploadedSongsCount: Long = 0L
)