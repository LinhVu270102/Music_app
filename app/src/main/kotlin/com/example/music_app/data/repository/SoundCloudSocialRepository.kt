package com.example.music_app.data.repository

import com.example.music_app.R
import com.example.music_app.data.model.Comment
import com.example.music_app.data.model.Song
import com.example.music_app.data.remote.soundcloud.SoundCloudRetrofitClient
import com.example.music_app.utils.AppException
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

data class SoundCloudTrackSocial(
    val trackId: String = "",
    val liked: Boolean = false,
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L
)

data class SoundCloudApiPlaylist(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val coverUrl: String = "",
    val songsCount: Int = 0
)

class SoundCloudSocialRepository {

    private val api = SoundCloudRetrofitClient.api
    private val auth = FirebaseAuth.getInstance()

    fun isSoundCloudSong(song: Song): Boolean {
        return song.source.equals("soundcloud", ignoreCase = true) ||
                song.id.startsWith("soundcloud_", ignoreCase = true) ||
                song.uploaderId.startsWith("soundcloud", ignoreCase = true)
    }

    fun isSoundCloudSongId(songId: String): Boolean {
        return songId.startsWith("soundcloud_", ignoreCase = true)
    }

    private fun currentUserId(): String {
        return auth.currentUser?.uid
            ?: throw AppException(R.string.not_logged_in)
    }

    private fun currentDisplayName(): String {
        val user = auth.currentUser
        return user?.displayName
            ?: user?.email
            ?: "Orange Music User"
    }

    private fun currentAvatarUrl(): String {
        return auth.currentUser?.photoUrl?.toString().orEmpty()
    }

    private fun readBodyOrThrow(
        isSuccessful: Boolean,
        code: Int,
        bodyText: String?
    ): String {
        if (!isSuccessful || bodyText.isNullOrBlank()) {
            throw IllegalStateException("SoundCloud server error: $code")
        }

        return bodyText
    }

    suspend fun getTrackSocial(songId: String): SoundCloudTrackSocial {
        val response = api.getTrackSocial(
            trackId = songId,
            userId = currentUserId()
        )

        val body = readBodyOrThrow(
            isSuccessful = response.isSuccessful,
            code = response.code(),
            bodyText = response.body()?.string()
        )

        val json = JSONObject(body)

        return SoundCloudTrackSocial(
            trackId = json.optString("trackId"),
            liked = json.optBoolean("liked"),
            likesCount = json.optLong("likesCount"),
            commentsCount = json.optLong("commentsCount")
        )
    }

    suspend fun toggleTrackLike(songId: String): SoundCloudTrackSocial {
        val response = api.toggleSoundCloudTrackLike(
            mapOf(
                "trackId" to songId,
                "userId" to currentUserId()
            )
        )

        val body = readBodyOrThrow(
            isSuccessful = response.isSuccessful,
            code = response.code(),
            bodyText = response.body()?.string()
        )

        val json = JSONObject(body)

        return SoundCloudTrackSocial(
            trackId = json.optString("trackId"),
            liked = json.optBoolean("liked"),
            likesCount = json.optLong("likesCount")
        )
    }

    suspend fun getComments(songId: String): List<Comment> {
        val response = api.getTrackComments(songId)

        val body = readBodyOrThrow(
            isSuccessful = response.isSuccessful,
            code = response.code(),
            bodyText = response.body()?.string()
        )

        val json = JSONObject(body)
        val commentsJson = json.optJSONArray("comments") ?: return emptyList()

        val comments = mutableListOf<Comment>()

        for (index in 0 until commentsJson.length()) {
            val item = commentsJson.optJSONObject(index) ?: continue

            comments.add(
                Comment(
                    id = item.optString("id"),
                    songId = item.optString("trackId", songId),
                    userId = item.optString("userId"),
                    displayName = item.optString("displayName"),
                    avatarUrl = item.optString("avatarUrl"),
                    content = item.optString("content"),
                    timelinePositionMs = item.optLong("timelinePositionMs"),
                    isDeleted = item.optBoolean("isDeleted"),
                    createdAt = item.optLong("createdAt"),
                    updatedAt = item.optLong("updatedAt")
                )
            )
        }

        return comments.sortedByDescending { it.createdAt }
    }

    suspend fun addComment(
        songId: String,
        content: String,
        timelinePositionMs: Long
    ) {
        val response = api.addSoundCloudTrackComment(
            mapOf(
                "trackId" to songId,
                "userId" to currentUserId(),
                "displayName" to currentDisplayName(),
                "avatarUrl" to currentAvatarUrl(),
                "content" to content,
                "timelinePositionMs" to timelinePositionMs
            )
        )

        readBodyOrThrow(
            isSuccessful = response.isSuccessful,
            code = response.code(),
            bodyText = response.body()?.string()
        )
    }

    suspend fun deleteComment(
        songId: String,
        commentId: String
    ) {
        val response = api.deleteSoundCloudTrackComment(
            mapOf(
                "trackId" to songId,
                "commentId" to commentId,
                "userId" to currentUserId()
            )
        )

        readBodyOrThrow(
            isSuccessful = response.isSuccessful,
            code = response.code(),
            bodyText = response.body()?.string()
        )
    }

    suspend fun getUserApiPlaylists(): List<SoundCloudApiPlaylist> {
        val response = api.getUserApiPlaylists(currentUserId())

        val body = readBodyOrThrow(
            isSuccessful = response.isSuccessful,
            code = response.code(),
            bodyText = response.body()?.string()
        )

        val json = JSONObject(body)
        val results = json.optJSONArray("results") ?: return emptyList()

        val playlists = mutableListOf<SoundCloudApiPlaylist>()

        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue

            playlists.add(
                SoundCloudApiPlaylist(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    description = item.optString("description"),
                    coverUrl = item.optString("coverUrl"),
                    songsCount = item.optInt("songsCount")
                )
            )
        }

        return playlists
    }

    suspend fun createUserApiPlaylist(name: String): SoundCloudApiPlaylist {
        val response = api.createUserApiPlaylist(
            mapOf(
                "userId" to currentUserId(),
                "name" to name,
                "ownerName" to currentDisplayName(),
                "isPublic" to true
            )
        )

        val body = readBodyOrThrow(
            isSuccessful = response.isSuccessful,
            code = response.code(),
            bodyText = response.body()?.string()
        )

        val playlist = JSONObject(body).optJSONObject("playlist")
            ?: return SoundCloudApiPlaylist()

        return SoundCloudApiPlaylist(
            id = playlist.optString("id"),
            name = playlist.optString("name"),
            description = playlist.optString("description"),
            coverUrl = playlist.optString("coverUrl"),
            songsCount = playlist.optInt("songsCount")
        )
    }

    suspend fun addTrackToUserApiPlaylist(
        playlistId: String,
        song: Song
    ) {
        val response = api.addTrackToUserApiPlaylist(
            mapOf(
                "playlistId" to playlistId,
                "track" to song.toSoundCloudTrackBody()
            )
        )

        readBodyOrThrow(
            isSuccessful = response.isSuccessful,
            code = response.code(),
            bodyText = response.body()?.string()
        )
    }

    private fun Song.toSoundCloudTrackBody(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "soundCloudId" to soundCloudId,
            "title" to title,
            "artist" to artist,
            "coverUrl" to coverUrl,
            "duration" to duration,
            "genre" to genre,
            "permalinkUrl" to permalinkUrl,
            "plays" to plays,
            "likes" to likes,
            "commentsCount" to commentsCount,
            "streamable" to streamable,
            "access" to access,
            "source" to source.ifBlank { "soundcloud" },
            "sourceLabel" to "SoundCloud",
            "uploaderId" to uploaderId.ifBlank { "soundcloud" }
        )
    }
}