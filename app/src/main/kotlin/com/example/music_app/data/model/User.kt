package com.example.music_app.data.model

import com.example.music_app.data.model.enums.AccountStatus
import com.example.music_app.data.model.enums.UserRole
import com.google.firebase.firestore.Exclude

data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val bio: String = "",

    // Thông tin hồ sơ mở rộng
    val fullName: String = "",
    val phoneNumber: String = "",
    val dateOfBirth: Long = 0L,
    val gender: String = "",
    val country: String = "",

    // Sở thích âm nhạc dùng cho AI gợi ý
    val favoriteGenres: List<String> = emptyList(),
    val musicMoodTags: List<String> = emptyList(),

    // Role
    val role: String = UserRole.USER.value,
    val accountStatus: String = AccountStatus.ACTIVE.value,

    // Time
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastLoginAt: Long = 0L,

    // Stats
    val likedSongsCount: Long = 0L,
    val playlistsCount: Long = 0L,
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    val uploadedSongsCount: Long = 0L
) {
    @get:Exclude
    val roleType: UserRole
        get() = UserRole.from(role)

    @get:Exclude
    val accountStatusType: AccountStatus
        get() = AccountStatus.from(accountStatus)
}
