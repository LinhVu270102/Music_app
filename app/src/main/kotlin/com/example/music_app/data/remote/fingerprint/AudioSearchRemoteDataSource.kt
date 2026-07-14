package com.example.music_app.data.remote.fingerprint

import com.example.music_app.data.model.AudioSearchMatch
import com.example.music_app.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Remote access for audio fingerprint search against the local development backend. */
class AudioSearchRemoteDataSource(
    private val baseUrl: String = LOCAL_SERVER_BASE_URL
) {

    suspend fun searchByAudioBase64(
        audioBase64: String,
        fileExtension: String,
        limit: Int
    ): List<AudioSearchMatch> = withContext(Dispatchers.IO) {
        val cleanAudioBase64 = audioBase64.trim()
        if (cleanAudioBase64.isBlank()) return@withContext emptyList()

        val requestBody = JSONObject()
            .put("audioBase64", cleanAudioBase64)
            .put("fileExtension", fileExtension.ifBlank { DEFAULT_FILE_EXTENSION })
            .put("limit", limit.coerceIn(MIN_LIMIT, MAX_LIMIT))
            .toString()

        val connection = URL("$baseUrl/fingerprint/search").openConnection() as HttpURLConnection

        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            connection.outputStream.use { outputStream ->
                outputStream.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseBody = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { reader -> reader.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { reader ->
                    reader.readText()
                }.orEmpty()
            }

            if (responseCode !in 200..299) {
                throw IOException("Audio search failed: code=$responseCode, body=$responseBody")
            }

            parseMatches(responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseMatches(responseBody: String): List<AudioSearchMatch> {
        val root = JSONObject(responseBody)
        val matches = root.optJSONArray("matches") ?: return emptyList()
        val result = mutableListOf<AudioSearchMatch>()

        for (index in 0 until matches.length()) {
            val matchJson = matches.optJSONObject(index) ?: continue
            val songJson = matchJson.optJSONObject("song") ?: continue
            val songId = matchJson.optString("songId")
            val song = parseSong(songJson, songId)

            if (song.id.isBlank()) continue

            result += AudioSearchMatch(
                songId = song.id,
                matchType = matchJson.optString("matchType"),
                score = matchJson.optDouble("score", 0.0),
                song = song
            )
        }

        return result
    }

    private fun parseSong(songJson: JSONObject, fallbackSongId: String): Song {
        return Song(
            id = songJson.optString("id").ifBlank { fallbackSongId },
            title = songJson.optString("title"),
            artist = songJson.optString("artist"),
            coverUrl = songJson.optString("coverUrl"),
            songUrl = songJson.optString("songUrl"),
            duration = songJson.optInt("duration", 0),
            uploaderId = songJson.optString("uploaderId"),
            genre = songJson.optString("genre"),
            status = songJson.optString("status"),
            fingerprintStatus = songJson.optString("fingerprintStatus")
        )
    }

    private companion object {
        private const val LOCAL_SERVER_BASE_URL = "http://10.0.2.2:3000"
        private const val DEFAULT_FILE_EXTENSION = "m4a"
        private const val TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MIN_LIMIT = 1
        private const val MAX_LIMIT = 10
    }
}
