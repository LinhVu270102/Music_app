package com.example.music_app.data.model

data class Report(
    val id: String = "",

    // Đối tượng bị report: song/comment/user
    val targetId: String = "",
    val targetType: String = ReportTargetType.SONG,

    // Người report
    val reporterId: String = "",
    val reporterName: String = "",

    // Nội dung report
    val reason: String = "",
    val description: String = "",

    // Trạng thái xử lý
    val status: String = ReportStatus.PENDING,
    val reviewedBy: String = "",
    val reviewedAt: Long = 0L,
    val adminNote: String = "",

    // Time
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

object ReportTargetType {
    const val SONG = "song"
    const val COMMENT = "comment"
    const val USER = "user"
}

object ReportStatus {
    const val PENDING = "pending"
    const val REVIEWED = "reviewed"
    const val REJECTED = "rejected"
    const val RESOLVED = "resolved"
}