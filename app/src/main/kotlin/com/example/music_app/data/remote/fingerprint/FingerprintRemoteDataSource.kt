package com.example.music_app.data.remote.fingerprint

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Remote access to the local fingerprint backend used by the development server. */
class FingerprintRemoteDataSource(
    private val baseUrl: String = LOCAL_SERVER_BASE_URL
) {

    suspend fun enqueueSongFingerprintProcessing(songId: String): Boolean = withContext(Dispatchers.IO) {
        if (songId.isBlank()) return@withContext false

        runCatching {
            val encodedSongId = URLEncoder.encode(songId, UTF_8)
            val requestUrl = URL("$baseUrl/fingerprint/songs/$encodedSongId/process?async=true")
            val connection = requestUrl.openConnection() as HttpURLConnection

            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")

            val responseCode = connection.responseCode
            val success = responseCode in 200..299

            if (!success) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { reader ->
                    reader.readText()
                }.orEmpty()

                Log.w(TAG, "enqueue fingerprint failed: songId=$songId, code=$responseCode, body=$errorBody")
            }

            connection.disconnect()
            success
        }.onFailure { error ->
            Log.w(TAG, "enqueue fingerprint failed: songId=$songId, message=${error.message}", error)
        }.getOrDefault(false)
    }

    private companion object {
        private const val TAG = "FingerprintRemote"
        private const val LOCAL_SERVER_BASE_URL = "http://10.0.2.2:3000"
        private const val TIMEOUT_MS = 3_000
        private const val UTF_8 = "UTF-8"
    }
}
