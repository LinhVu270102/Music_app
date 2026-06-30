package com.example.music_app.data.model

import com.example.music_app.data.model.enums.AppNotificationTargetType
import com.example.music_app.data.model.enums.AppNotificationType
import com.google.firebase.firestore.Exclude

data class AppNotification(
    val id: String = "",

    // Người nhận thông báo
    val receiverId: String = "",

    // Người tạo hành động: follow/comment/like/admin
    val actorId: String = "",
    val actorName: String = "",
    val actorAvatarUrl: String = "",

    // Nội dung
    val type: String = AppNotificationType.GENERAL.value,
    val title: String = "",
    val message: String = "",

    // Liên kết tới dữ liệu liên quan
    val targetId: String = "",
    val targetType: String = AppNotificationTargetType.NONE.value,

    // Trạng thái
    val isRead: Boolean = false,

    // Time
    val createdAt: Long = 0L
) {
    @get:Exclude
    val notificationType: AppNotificationType
        get() = AppNotificationType.from(type)

    @get:Exclude
    val targetKind: AppNotificationTargetType
        get() = AppNotificationTargetType.from(targetType)
}
