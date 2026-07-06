package com.example.music_app.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Comment(
    val id: String = "",
    val songId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val content: String = "",
    val parentCommentId: String = "",
    val replyToUserId: String = "",
    val replyToDisplayName: String = "",
    val timelinePositionMs: Long = 0L,
    val likesCount: Long = 0L,
    val isLikedByCurrentUser: Boolean = false,
    val isLikedBySongOwner: Boolean = false,
    val songOwnerAvatarUrl: String = "",
    val reportsCount: Long = 0L,
    val isDeleted: Boolean = false,
    val deletedBy: String = "",
    val deletedAt: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
