package com.example.music_app.data.remote.stream

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Android emulators reach a server running on the development machine through
 * 10.0.2.2. For a physical phone, replace this address with the computer's LAN IP.
 */
object LocalMusicStreamClient {
    private const val BASE_URL = "http://10.0.2.2:3000/"

    val api: LocalMusicStreamApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LocalMusicStreamApi::class.java)
    }
}
