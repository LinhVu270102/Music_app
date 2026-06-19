package com.example.music_app.data.model

data class AppNotification(
    val id: String = "",

    // Người nhận thông báo
    val receiverId: String = "",

    // Người tạo hành động: follow/comment/like/admin
    val actorId: String = "",
    val actorName: String = "",
    val actorAvatarUrl: String = "",

    // Nội dung
    val type: String = AppNotificationType.GENERAL,
    val title: String = "",
    val message: String = "",

    // Liên kết tới dữ liệu liên quan
    val targetId: String = "",
    val targetType: String = "",

    // Trạng thái
    val isRead: Boolean = false,

    // Time
    val createdAt: Long = 0L
)

object AppNotificationType {
    const val GENERAL = "general"
    const val SONG_APPROVED = "song_approved"
    const val SONG_REJECTED = "song_rejected"
    const val NEW_FOLLOWER = "new_follower"
    const val NEW_COMMENT = "new_comment"
    const val NEW_LIKE = "new_like"
    const val REPORT_RESOLVED = "report_resolved"
}