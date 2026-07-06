package com.example.music_app.ui.player

import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.MusicInteractionRepository
import com.example.music_app.player.PlayerManager
import com.example.music_app.player.PlaybackContext
import com.example.music_app.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object PlaybackLauncher {

    private var isOpeningPlayer = false
    private val musicInteractionRepository = MusicInteractionRepository()

    fun openPlayer(
        fragment: Fragment,
        song: Song,
        playlist: List<Song> = emptyList(),
        playlistId: String = "",
        playlistName: String = "",
        playlistOwnerId: String = "",
        playlistCoverUrl: String = ""
    ) {
        if (isOpeningPlayer) return
        isOpeningPlayer = true

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                val queue = (playlist.ifEmpty { listOf(song) })
                    .filter { item -> item.canEnterPlaybackQueue() }
                    .distinctBy(Song::id)

                val preparedSong = withContext(Dispatchers.IO) {
                    musicInteractionRepository.preparePlayableSong(song)
                }
                val playableQueue = queue
                    .map { item ->
                        if (item.id == preparedSong.id) preparedSong else item
                    }
                    .filter { item -> item.isPlayableForPlayback() }

                val playableSong = playableQueue.firstOrNull { item -> item.id == song.id }
                if (playableSong == null) {
                    showPlaybackUnavailable(fragment)
                    return@launch
                }

                if (!isStreamSourceAvailable(playableSong.songUrl)) {
                    showSoundCloudServerUnavailable(fragment)
                    return@launch
                }

                (fragment.activity as? MainActivity)?.startMusicService()
                PlayerManager.playPlaylist(
                    songs = playableQueue,
                    startSong = playableSong,
                    context = PlaybackContext(
                        playlistId = playlistId,
                        playlistName = playlistName,
                        playlistOwnerId = playlistOwnerId,
                        playlistCoverUrl = playlistCoverUrl
                    )
                )

                fragment.parentFragmentManager.commit {
                    replace(R.id.fragmentContainer, PlayerFragment.newInstance(playableSong.id))
                    addToBackStack(null)
                }
            } catch (_: Exception) {
                showPlaybackUnavailable(fragment)
            } finally {
                isOpeningPlayer = false
            }
        }
    }

    private fun showPlaybackUnavailable(fragment: Fragment) {
        Toast.makeText(
            fragment.requireContext(),
            fragment.getString(R.string.playback_failed),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showSoundCloudServerUnavailable(fragment: Fragment) {
        Toast.makeText(
            fragment.requireContext(),
            fragment.getString(R.string.soundcloud_server_unavailable),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun Song.canEnterPlaybackQueue(): Boolean {
        return id.isNotBlank() && !isDeleted
    }

    private fun Song.isPlayableForPlayback(): Boolean {
        return id.isNotBlank() && songUrl.isNotBlank() && !isDeleted
    }

    private suspend fun isStreamSourceAvailable(songUrl: String): Boolean {
        if (!songUrl.isLocalSoundCloudProxyUrl()) return true

        return withContext(Dispatchers.IO) {
            runCatching {
                val parsedUrl = URL(songUrl)
                val probeUrl = URL("${parsedUrl.protocol}://${parsedUrl.host}:${parsedUrl.port}/")
                val connection = probeUrl.openConnection() as HttpURLConnection

                connection.connectTimeout = LOCAL_PROXY_TIMEOUT_MS
                connection.readTimeout = LOCAL_PROXY_TIMEOUT_MS
                connection.requestMethod = "GET"
                connection.connect()

                // A 404 still proves the local server is reachable; connection
                // refusal/timeouts are what make proxy-backed songs fail.
                connection.responseCode
                connection.disconnect()
                true
            }.getOrDefault(false)
        }
    }

    private fun String.isLocalSoundCloudProxyUrl(): Boolean {
        return contains("/soundcloud/proxy/media", ignoreCase = true) &&
            (
                contains("10.0.2.2", ignoreCase = true) ||
                    contains("127.0.0.1", ignoreCase = true) ||
                    contains("localhost", ignoreCase = true)
            )
    }

    private const val LOCAL_PROXY_TIMEOUT_MS = 2_000
}
