package com.example.music_app.data.model

import com.example.music_app.data.model.enums.ReportStatus
import com.example.music_app.data.model.enums.ReportTargetType
import com.google.firebase.firestore.Exclude

data class Report(
    val id: String = "",

    // Đối tượng bị report: song/comment/user
    val targetId: String = "",
    val targetType: String = ReportTargetType.SONG.value,

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
