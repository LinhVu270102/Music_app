package com.example.music_app.data.model

data class AdminDashboardStats(
    val pendingSongs: Int = 0,
    val approvedSongs: Int = 0,
    val rejectedSongs: Int = 0,
    val hiddenSongs: Int = 0,
    val pendingReports: Int = 0,
    val reportedSongs: Int = 0
)