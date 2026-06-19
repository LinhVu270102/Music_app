package com.example.music_app.data.remote.soundcloud

import retrofit2.http.GET
import retrofit2.http.Query

interface SoundCloudProxyApi {

    @GET("searchSoundCloudTracks")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10
    ): SoundCloudSearchResponse

    @GET("getSoundCloudStreamUrl")
    suspend fun getStreamUrl(
        @Query("trackId") trackId: Long
    ): SoundCloudStreamResponse
}