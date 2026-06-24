package com.example.music_app.data.model

data class Song(
    val id: String = "",

    // Thông tin bài hát
    val title: String = "",
    val artist: String = "",
    val coverUrl: String = "",
    val songUrl: String = "",
    val duration: Int = 0,

    // Thống kê
    val plays: Long = 0L,
    val likes: Long = 0L,
    val commentsCount: Long = 0L,
    val reportsCount: Long = 0L,

    // Người đăng
    val uploaderId: String = "",

    // Phân loại / gợi ý
    val genre: String = "",
    val tags: List<String> = emptyList(),

    // Kiểm duyệt
    val status: String = SongStatus.PENDING,
    val rejectReason: String = "",
    val reviewedBy: String = "",
    val reviewedAt: Long = 0L,

    // Quyền tương tác của bài đăng
    val allowComments: Boolean = true,

    // Xóa mềm bài hát
    val isDeleted: Boolean = false,
    val deletedAt: Long = 0L,
    val deletedBy: String = "",

    // Thời gian
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,

    // The player uses the Firebase Storage URL above as its single audio source.
)
