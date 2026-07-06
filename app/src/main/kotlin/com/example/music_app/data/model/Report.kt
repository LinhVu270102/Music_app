package com.example.music_app.data.model

import com.example.music_app.data.model.enums.ReportStatus
import com.example.music_app.data.model.enums.ReportTargetType
import com.google.firebase.firestore.Exclude

data class Report(
    val id: String = "",

    // Đối tượng bị report: song/comment/user
    val targetId: String = "",
    val targetType: String = ReportTargetType.SONG.value,
    val targetOwnerId: String = "",

    // Bài hát chứa nội dung bị report.
    // Với report bài hát: songId == targetId, songOwnerId là uploaderId.
    // Với report bình luận: songId là bài hát chứa bình luận đó.
    val songId: String = "",
    val songOwnerId: String = "",

    // Thông tin hiển thị nhanh cho kiểm duyệt viên.
    val targetTitle: String = "",
    val targetSubtitle: String = "",
    val targetPreview: String = "",

    // Người report
    val reporterId: String = "",
    val reporterName: String = "",

    // Nội dung report
    val reason: String = "",
    val description: String = "",

    // Trạng thái xử lý
    val status: String = ReportStatus.PENDING.value,
    val reviewedBy: String = "",
    val reviewedAt: Long = 0L,
    val adminNote: String = "",

    // Time
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    @get:Exclude
    val targetKind: ReportTargetType
        get() = ReportTargetType.from(targetType)

    @get:Exclude
    val statusType: ReportStatus
        get() = ReportStatus.from(status)
}
