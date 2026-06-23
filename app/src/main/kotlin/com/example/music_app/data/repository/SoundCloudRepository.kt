package com.example.music_app.data.repository

import android.util.Log
import com.example.music_app.data.model.Song
import com.example.music_app.data.remote.soundcloud.SoundCloudRetrofitClient
import com.example.music_app.data.remote.soundcloud.getSoundCloudTrackId
import com.example.music_app.data.remote.soundcloud.toSong
import com.example.music_app.data.remote.soundcloud.model.SoundCloudArtistProfileResponse
import com.example.music_app.data.model.Playlist
import org.json.JSONArray
import org.json.JSONObject

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

    /** Returns the public track count used to enrich a SoundCloud artist search result. */
    suspend fun getArtistTrackCount(artistName: String, limit: Int = 20): Int {
        if (artistName.isBlank()) return 0

        return try {
            val response = api.getArtistTracks(
                artist = artistName,
                limit = limit
            )

            if (!response.isSuccessful) return 0

            val body = response.body()?.string().orEmpty()
            if (body.isBlank()) return 0

            JSONObject(body).optJSONArray("results")?.length() ?: 0
        } catch (_: Exception) {
            0
        }
    }

    suspend fun getArtistProfile(
        artistName: String,
        limit: Int = 20
    ): SoundCloudArtistProfileResponse {
        return api.getArtistProfile(
            artist = artistName,
            limit = limit
        )
    }

    suspend fun searchPlaylists(
        query: String,
        limit: Int = 20
    ): List<Playlist> {
        if (query.isBlank()) return emptyList()

        val response = api.getApiPlaylist(
            query = query,
            limit = limit
        )

        val rawBody = response.body()?.string().orEmpty()

        if (!response.isSuccessful || rawBody.isBlank()) {
            return emptyList()
        }

        val root = JSONObject(rawBody)

        val playlistsArray = when {
            root.has("playlists") -> root.optJSONArray("playlists")
            root.has("results") -> root.optJSONArray("results")
            root.has("data") -> root.optJSONArray("data")
            else -> null
        } ?: JSONArray()

        return List(playlistsArray.length()) { index ->
            playlistsArray.optJSONObject(index)
        }.mapNotNull { json ->
            json?.toPlaylist()
        }
            .filter { playlist ->
                playlist.name.contains(query, ignoreCase = true) ||
                        playlist.description.contains(query, ignoreCase = true)
            }
            .distinctBy { playlist -> playlist.id }
            .take(limit)
    }
    private fun JSONObject.toPlaylist(): Playlist? {
        val rawId = optString("id")
            .ifBlank { optString("playlistId") }
            .ifBlank { optString("soundCloudId") }
            .ifBlank { optString("urn") }

        val name = optString("title")
            .ifBlank { optString("name") }

        if (rawId.isBlank() || name.isBlank()) return null

        val coverUrl = optString("coverUrl")
            .ifBlank { optString("artwork_url") }
            .ifBlank { optString("artworkUrl") }
            .ifBlank { optString("image") }
            .ifBlank { optString("thumbnail") }

        val description = optString("description")
            .ifBlank { optString("userName") }
            .ifBlank { optString("username") }

        val tracksCount = optLong("tracksCount", -1L)
            .takeIf { it >= 0L }
            ?: optLong("track_count", 0L)

        val normalizedId =
            if (rawId.startsWith("soundcloud_playlist_", ignoreCase = true)) {
                rawId
            } else {
                "soundcloud_playlist_$rawId"
            }

        return Playlist(
            id = normalizedId,
            name = name,
            description = description,
            coverUrl = coverUrl,
            ownerId = "soundcloud",
            isPublic = true,
            songsCount = tracksCount,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
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
