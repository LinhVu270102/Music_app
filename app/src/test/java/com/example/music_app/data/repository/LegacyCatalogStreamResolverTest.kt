package com.example.music_app.data.repository

import com.example.music_app.data.model.Song
import com.example.music_app.data.remote.stream.LocalMusicStreamApi
import com.example.music_app.data.remote.stream.LocalMusicStreamResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyCatalogStreamResolverTest {

    @Test
    fun resolvesImportedTrackFromNumericDocumentIdSuffix() = runBlocking {
        var requestedTrackId: Long? = null
        val resolver = LegacyCatalogStreamResolver(
            api = object : LocalMusicStreamApi {
                override suspend fun getStreamUrl(trackId: Long): LocalMusicStreamResponse {
                    requestedTrackId = trackId
                    return LocalMusicStreamResponse("https://proxy.example/stream.mp3")
                }
            }
        )

        val resolvedSong = resolver.resolve(
            Song(
                id = "imported_1068221248",
                title = "Legacy track"
            )
        )

        assertEquals(1_068_221_248L, requestedTrackId)
        assertEquals("https://proxy.example/stream.mp3", resolvedSong.songUrl)
    }

    @Test
    fun keepsSongsWithoutNumericStreamKeyUnchanged() = runBlocking {
        val resolver = LegacyCatalogStreamResolver(
            api = object : LocalMusicStreamApi {
                override suspend fun getStreamUrl(trackId: Long): LocalMusicStreamResponse {
                    throw AssertionError("The proxy must not be called")
                }
            }
        )
        val song = Song(id = "uploaded_track", songUrl = "")

        assertEquals(song, resolver.resolve(song))
    }

    @Test
    fun replacesCachedLocalhostProxyUrlForTheEmulator() = runBlocking {
        val resolver = LegacyCatalogStreamResolver(
            api = object : LocalMusicStreamApi {
                override suspend fun getStreamUrl(trackId: Long): LocalMusicStreamResponse {
                    return LocalMusicStreamResponse(
                        "http://localhost:3000/soundcloud/proxy/media?url=https%3A%2F%2Fexample.com"
                    )
                }
            }
        )

        val resolvedSong = resolver.resolve(Song(id = "imported_1068221248"))

        assertEquals(
            "http://10.0.2.2:3000/soundcloud/proxy/media?url=https%3A%2F%2Fexample.com",
            resolvedSong.songUrl
        )
    }
}
