package com.example.music_app.data.remote.soundcloud.model

data class SoundCloudArtistProfileResponse(
    val profile: SoundCloudArtistProfileDto = SoundCloudArtistProfileDto(),
    val results: List<SoundCloudSurfaceTrackDto> = emptyList()
)

data class SoundCloudArtistProfileDto(
    val id: String = "",
    val artistName: String = "",
    val displayName: String = "",
    val username: String = "",
    val source: String = "",
    val sourceLabel: String = "",
    val avatarUrl: String = "",
    val bannerUrl: String = "",
    val bio: String = "",
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    val tracksCount: Int = 0,
    val playlistsCount: Int = 0
)

data class SoundCloudSurfaceTrackDto(
    val id: String = "",
    val soundCloudId: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val coverUrl: String = "",
    val duration: Int = 0,
    val genre: String = "",
    val permalinkUrl: String = "",
    val playbackCount: Long = 0L,
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val plays: Long = 0L,
    val likes: Long = 0L,
    val streamable: Boolean = false,
    val access: String = "",
    val source: String = "",
    val sourceLabel: String = "",
    val uploaderId: String = "",
    val uploaderName: String = "",
    val uploaderUsername: String = "",
    val uploaderAvatarUrl: String = "",
    val uploaderPermalinkUrl: String = ""
)