package com.example.music_app.data.repository

import com.example.music_app.data.model.Song
import com.example.music_app.data.model.SoundCloudTrackSocial
import com.example.music_app.data.remote.soundcloud.SoundCloudRetrofitClient
import com.example.music_app.data.remote.soundcloud.SoundCloudSession
import org.json.JSONObject

class SoundCloudSocialRepository {
    private val api = SoundCloudRetrofitClient.api
    private val session = SoundCloudSession()

    fun isSoundCloudSong(song: Song): Boolean {
        return song.source.equals("soundcloud", ignoreCase = true) ||
                song.id.startsWith("soundcloud_", ignoreCase = true) ||
                song.uploaderId.startsWith("soundcloud", ignoreCase = true)
    }

    suspend fun getTrackSocial(songId: String): SoundCloudTrackSocial {
        val response = api.getTrackSocial(
            trackId = songId,
            userId = session.requireUserId()
        )

        val body = session.requireBody(
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
                "userId" to session.requireUserId()
            )
        )

        val body = session.requireBody(
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

}
