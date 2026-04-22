package com.example.music_app.data.model

data class Song(
    val id: String,            // ID duy nhất
    val title: String,         // Tên bài hát
    val artist: String,        // Nghệ sĩ
    val coverResId: Int,       // Ảnh bìa (drawable resource)
    val duration: Int,         // Thời lượng (ms)
    var likes: Int = 0         // Số lượt thích (mặc định 0)
)