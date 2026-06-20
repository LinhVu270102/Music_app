package com.example.music_app.data.remote.soundcloud

import com.example.music_app.data.remote.soundcloud.model.SoundCloudSearchResponse
import com.example.music_app.data.remote.soundcloud.model.SoundCloudStreamResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import com.example.music_app.data.remote.soundcloud.model.SoundCloudArtistProfileResponse
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

    // =========================
    // Profile / Playlist surface
    // =========================

    @GET("getSoundCloudArtistProfile")
    suspend fun getArtistProfile(
        @Query("artist") artist: String,
        @Query("limit") limit: Int = 20
    ): SoundCloudArtistProfileResponse

    @GET("getSoundCloudArtistTracks")
    suspend fun getArtistTracks(
        @Query("artist") artist: String,
        @Query("limit") limit: Int = 20
    ): Response<ResponseBody>

    @GET("getSoundCloudApiPlaylist")
    suspend fun getApiPlaylist(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): Response<ResponseBody>

    // =========================
    // Orange user surface
    // =========================

    @POST("upsertOrangeMusicUser")
    suspend fun upsertOrangeMusicUser(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<ResponseBody>

    @GET("getOrangeMusicUser")
    suspend fun getOrangeMusicUser(
        @Query("userId") userId: String
    ): Response<ResponseBody>

    // =========================
    // User API playlist
    // =========================

    @GET("getUserApiPlaylists")
    suspend fun getUserApiPlaylists(
        @Query("userId") userId: String
    ): Response<ResponseBody>

    @POST("createUserApiPlaylist")
    suspend fun createUserApiPlaylist(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<ResponseBody>

    @POST("addTrackToUserApiPlaylist")
    suspend fun addTrackToUserApiPlaylist(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<ResponseBody>

    @POST("removeTrackFromUserApiPlaylist")
    suspend fun removeTrackFromUserApiPlaylist(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<ResponseBody>

    // =========================
    // Comment / Like
    // =========================

    @GET("getSoundCloudTrackComments")
    suspend fun getTrackComments(
        @Query("trackId") trackId: String
    ): Response<ResponseBody>

    @POST("addSoundCloudTrackComment")
    suspend fun addSoundCloudTrackComment(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<ResponseBody>

    @POST("deleteSoundCloudTrackComment")
    suspend fun deleteSoundCloudTrackComment(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<ResponseBody>

    @GET("getSoundCloudTrackSocial")
    suspend fun getTrackSocial(
        @Query("trackId") trackId: String,
        @Query("userId") userId: String
    ): Response<ResponseBody>

    @POST("toggleSoundCloudTrackLike")
    suspend fun toggleSoundCloudTrackLike(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<ResponseBody>
}