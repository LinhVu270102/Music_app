package com.example.music_app.data.repository

import com.example.music_app.data.model.Song
import com.example.music_app.data.remote.stream.LocalMusicStreamApi
import com.example.music_app.data.remote.stream.LocalMusicStreamClient

/** Resolves the temporary stream URL for catalog records imported before Firebase audio uploads. */
class LegacyCatalogStreamResolver(
    private val api: LocalMusicStreamApi = LocalMusicStreamClient.api
) {
    suspend fun resolve(song: Song): Song {
        if (song.songUrl.isNotBlank()) return song

        // Imported catalogue rows use a provider-neutral document ID whose final segment
        // is the numeric stream key (for example, "legacy_123456").
        val trackId = NUMERIC_STREAM_KEY.find(song.id)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: return song

        val streamUrl = api.getStreamUrl(trackId).streamUrl
        return song.copy(songUrl = normalizeEmulatorProxyUrl(streamUrl))
    }

    private companion object {
        val NUMERIC_STREAM_KEY = Regex("""(?:^|_)(\d+)$""")
        const val LOCALHOST_PROXY_BASE_URL = "http://localhost:3000/"
        const val LOOPBACK_PROXY_BASE_URL = "http://127.0.0.1:3000/"
        const val EMULATOR_PROXY_BASE_URL = "http://10.0.2.2:3000/"

        fun normalizeEmulatorProxyUrl(url: String): String {
            return url
                .replace(LOCALHOST_PROXY_BASE_URL, EMULATOR_PROXY_BASE_URL)
                .replace(LOOPBACK_PROXY_BASE_URL, EMULATOR_PROXY_BASE_URL)
        }
    }
}
