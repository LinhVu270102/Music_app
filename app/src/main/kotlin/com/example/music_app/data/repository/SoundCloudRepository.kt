package com.example.music_app.data.repository

import android.util.Log
import com.example.music_app.data.model.Song
import com.example.music_app.data.remote.soundcloud.SoundCloudRetrofitClient
import com.example.music_app.data.remote.soundcloud.getSoundCloudTrackId
import com.example.music_app.data.remote.soundcloud.toSong

class SoundCloudRepository {

    private val api = SoundCloudRetrofitClient.api

    suspend fun searchTracks(query: String, limit: Int = 10): List<Song> {
        if (query.isBlank()) return emptyList()

        val response = api.searchTracks(
            query = query,
            limit = limit
        )

        return response.results
            .filter { it.title.isNotBlank() }
            .filter { it.access.isBlank() || it.access == "playable" }
            .map { it.toSong() }
    }

    suspend fun getPlayableSong(song: Song): Song {
        val trackId = song.getSoundCloudTrackId()

        Log.d("SoundCloudRepository", "song.id = ${song.id}")
        Log.d("SoundCloudRepository", "song.soundCloudId = ${song.soundCloudId}")
        Log.d("SoundCloudRepository", "resolved trackId = $trackId")

        if (trackId == 0L) {
            Log.e("SoundCloudRepository", "Invalid SoundCloud trackId")
            return song
        }

        val response = api.getStreamUrl(trackId)

        Log.d("SoundCloudRepository", "streamUrl = ${response.streamUrl}")
        Log.d("SoundCloudRepository", "protocol = ${response.protocol}")
        Log.d("SoundCloudRepository", "mimeType = ${response.mimeType}")

        val newTags = song.tags
            .filterNot { it == "hls" || it == "progressive" }
            .toMutableList()

        if (
            response.protocol == "hls" ||
            response.mimeType.contains("mpegURL", ignoreCase = true) ||
            response.streamUrl.contains("/hls", ignoreCase = true) ||
            response.streamUrl.contains(".m3u8", ignoreCase = true)
        ) {
            newTags.add("hls")
        } else {
            newTags.add("progressive")
        }

        return song.copy(
            songUrl = response.streamUrl,
            soundCloudId = trackId,
            source = "soundcloud",
            streamable = true,
            access = if (response.access.isNotBlank()) response.access else song.access,
            duration = if (response.duration > 0) {
                response.duration
            } else {
                song.duration
            },
            tags = newTags
        )
    }
}