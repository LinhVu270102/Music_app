package com.example.music_app.model

data class Comment(
    val id: String,          // ID duy nhất cho comment
    val userId: String,      // ID người dùng viết comment
    val songId: String,      // ID bài hát hoặc playlist mà comment thuộc về
    val content: String,     // Nội dung bình luận
    val timestamp: Long,     // Thời gian đăng (epoch millis)
    var likes: Int = 0       // Số lượt thích cho comment
)
