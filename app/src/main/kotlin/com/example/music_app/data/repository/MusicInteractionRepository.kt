package com.example.music_app.data.repository

import android.util.Log
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Song
import com.example.music_app.data.firebase.firestore.PlaylistFirestoreDataSource
import com.example.music_app.data.firebase.firestore.SongFirestoreDataSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/** Persists local player interactions for the Firebase-backed catalog. */
class MusicInteractionRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val songFirestoreDataSource: SongFirestoreDataSource = SongFirestoreDataSource(firestore),
    private val playlistFirestoreDataSource: PlaylistFirestoreDataSource =
        PlaylistFirestoreDataSource(firestore)
) {

    /**
     * Ensures playback receives a fresh playable URL.
     *
     * Firestore is the catalog source of truth, but legacy SoundCloud CDN URLs
     * can expire. For those records we resolve a fresh local proxy URL right
     * before playback.
     */
    suspend fun preparePlayableSong(song: Song): Song {
        if (song.id.isBlank() || song.isDeleted) return song

        val normalizedLocalProxyUrl = song.songUrl.normalizeLocalProxyUrl()
        val normalizedSong = if (normalizedLocalProxyUrl != song.songUrl) {
            song.copy(songUrl = normalizedLocalProxyUrl)
        } else {
            song
        }

        if (!normalizedSong.shouldResolveFreshProxyUrl()) return normalizedSong

        val trackId = normalizedSong.soundCloudTrackIdOrNull() ?: return normalizedSong
        val resolvedUrl = resolveFreshProxyUrl(trackId).orEmpty()

        return if (resolvedUrl.isNotBlank()) {
            normalizedSong.copy(songUrl = resolvedUrl)
        } else {
            normalizedSong
        }
    }

    suspend fun saveRecentlyPlayed(song: Song) {
        val userId = currentUserIdOrNull() ?: return
        if (!song.canBeSavedToHistory()) return

        songFirestoreDataSource.saveRecentlyPlayed(userId, song)
    }

    suspend fun saveRecentlyPlayedPlaylist(playlist: Playlist?) {
        val userId = currentUserIdOrNull() ?: return
        val recentPlaylist = playlist ?: return
        if (!recentPlaylist.canBeSavedToHistory()) return

        playlistFirestoreDataSource.saveRecentlyPlayedPlaylist(userId, recentPlaylist)
    }

    private fun currentUserIdOrNull(): String? {
        return auth.currentUser?.uid?.takeIf(String::isNotBlank)
    }

    private fun Song.canBeSavedToHistory(): Boolean {
        return id.isNotBlank() && !isDeleted
    }

    private fun Playlist.canBeSavedToHistory(): Boolean {
        return id.isNotBlank() && name.isNotBlank()
    }

    private suspend fun resolveFreshProxyUrl(trackId: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val encodedTrackId = URLEncoder.encode(trackId, UTF_8)
            val requestUrl = URL("$LOCAL_STREAM_PROXY_BASE_URL/getStreamUrl?trackId=$encodedTrackId")
            val connection = requestUrl.openConnection() as HttpURLConnection

            connection.connectTimeout = STREAM_RESOLVE_TIMEOUT_MS
            connection.readTimeout = STREAM_RESOLVE_TIMEOUT_MS
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                Log.w(TAG, "resolveFreshProxyUrl failed: trackId=$trackId, code=$responseCode")
                connection.disconnect()
                return@runCatching null
            }

            val responseBody = connection.inputStream.bufferedReader().use { reader ->
                reader.readText()
            }
            connection.disconnect()

            JSONObject(responseBody)
                .optString("streamUrl")
                .takeIf(String::isNotBlank)
                ?.normalizeLocalProxyUrl()
        }.onFailure { error ->
            Log.w(TAG, "resolveFreshProxyUrl failed: trackId=$trackId, message=${error.message}", error)
        }.getOrNull()
    }

    private fun Song.shouldResolveFreshProxyUrl(): Boolean {
        if (!id.startsWith(SOUNDCLOUD_ID_PREFIX, ignoreCase = true)) return false
        if (songUrl.isBlank()) return true

        if (songUrl.isLocalProxyUrl()) {
            return songUrl.proxyTargetUrlOrNull()
                ?.contains("sndcdn.com", ignoreCase = true) == true
        }

        return songUrl.contains("sndcdn.com", ignoreCase = true) ||
            songUrl.contains("soundcloud.com", ignoreCase = true)
    }

    private fun Song.soundCloudTrackIdOrNull(): String? {
        return id
            .removePrefix(SOUNDCLOUD_ID_PREFIX)
            .takeIf { trackId -> trackId.isNotBlank() && trackId.all(Char::isDigit) }
    }

    private fun String.isLocalProxyUrl(): Boolean {
        return contains("/soundcloud/proxy/media", ignoreCase = true)
    }

    private fun String.proxyTargetUrlOrNull(): String? {
        if (!isLocalProxyUrl()) return null

        return runCatching {
            URL(this)
                .query
                .orEmpty()
                .split("&")
                .firstOrNull { parameter -> parameter.startsWith("url=") }
                ?.substringAfter("url=")
                ?.let { encodedUrl -> URLDecoder.decode(encodedUrl, UTF_8) }
                ?.takeIf(String::isNotBlank)
        }.getOrNull()
    }

    private fun String.normalizeLocalProxyUrl(): String {
        if (!isLocalProxyUrl()) return this

        return replace("http://localhost:3000", LOCAL_STREAM_PROXY_BASE_URL, ignoreCase = true)
            .replace("http://127.0.0.1:3000", LOCAL_STREAM_PROXY_BASE_URL, ignoreCase = true)
    }

    private companion object {
        private const val TAG = "MusicInteractionRepository"
        private const val SOUNDCLOUD_ID_PREFIX = "soundcloud_"
        private const val LOCAL_STREAM_PROXY_BASE_URL = "http://10.0.2.2:3000"
        private const val STREAM_RESOLVE_TIMEOUT_MS = 15_000
        private const val UTF_8 = "UTF-8"
    }
}
