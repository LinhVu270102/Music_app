package com.example.music_app.data.repository

import com.example.music_app.data.model.AudioSearchMatch
import com.example.music_app.data.remote.fingerprint.AudioSearchRemoteDataSource

class AudioSearchRepository(
    private val remoteDataSource: AudioSearchRemoteDataSource = AudioSearchRemoteDataSource()
) {

    suspend fun searchByAudioSample(
        audioBase64: String,
        fileExtension: String,
        limit: Int = DEFAULT_LIMIT
    ): List<AudioSearchMatch> {
        return remoteDataSource.searchByAudioBase64(
            audioBase64 = audioBase64,
            fileExtension = fileExtension,
            limit = limit
        )
    }

    private companion object {
        private const val DEFAULT_LIMIT = 10
    }
}
