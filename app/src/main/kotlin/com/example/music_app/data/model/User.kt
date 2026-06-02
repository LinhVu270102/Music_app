package com.example.music_app.data.model

data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val bio: String = "",
    val role: String = "listener",
    val accountStatus: String = "active",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastLoginAt: Long = 0L
)