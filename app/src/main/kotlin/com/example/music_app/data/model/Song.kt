package com.example.music_app.data.model

data class Song(
    val id: String = "",       // ID duy nhất
    val title: String = "",    // Tên bài hát
    val artist: String = "",   // Nghệ sĩ
    val coverUrl: String = "", // URL ảnh bìa
    val duration: Int = 0,     // Thời lượng (ms)
    val songUrl: String = "",  // URL file nhạc
    val plays: Long = 0,       // Số lượt nghe
    val likes: Long = 0,       // Số lượt thích
    val uploaderId: String = "" // ID người upload
)