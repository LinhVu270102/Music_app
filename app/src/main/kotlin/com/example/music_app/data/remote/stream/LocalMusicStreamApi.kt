package com.example.music_app.data.remote.stream

import retrofit2.http.GET
import retrofit2.http.Query

/** Minimal contract for the local stream resolver used by legacy catalog tracks. */
interface LocalMusicStreamApi {
    @GET("getStreamUrl")
    suspend fun getStreamUrl(
        @Query("trackId") trackId: Long
    ): LocalMusicStreamResponse
}

data class LocalMusicStreamResponse(
    val streamUrl: String = ""
)
