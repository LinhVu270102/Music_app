package com.example.music_app.data.repository

import com.example.music_app.data.model.Song
import com.example.music_app.data.remote.soundcloud.SoundCloudRetrofitClient
import com.example.music_app.data.remote.soundcloud.getSoundCloudTrackId
import com.example.music_app.data.remote.soundcloud.toSong

class SoundCloudRepository {

    private val api = SoundCloudRetrofitClient.api

    suspend fun searchTracks(query: String, limit: Int = 20): List<Song> {
        if (query.isBlank()) return emptyList()

        val response = api.searchTracks(
            query = query,
            limit = limit
        )

        return response.results
            .filter { it.soundCloudId != 0L }
            .filter { it.title.isNotBlank() }
            .filter { it.streamable }
            .filter { it.access.isBlank() || it.access == "playable" }
            .map { it.toSong() }
    }

    suspend fun getPlayableSong(song: Song): Song {
        val trackId = song.getSoundCloudTrackId()

        if (trackId == 0L) {
            return song
        }

        val response = api.getStreamUrl(trackId)

        return song.copy(
            songUrl = response.streamUrl,
            duration = if (response.duration > 0) response.duration else song.duration
        )
    }
}