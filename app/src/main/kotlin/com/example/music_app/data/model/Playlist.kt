package com.example.music_app.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Playlist(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val coverUrl: String = "",
    val ownerId: String = "",
    val isPublic: Boolean = true,
    val songsCount: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
