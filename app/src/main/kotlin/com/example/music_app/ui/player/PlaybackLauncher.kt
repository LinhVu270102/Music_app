package com.example.music_app.ui.player

import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.LegacyCatalogStreamResolver
import com.example.music_app.player.PlayerManager
import com.example.music_app.player.PlaybackContext
import com.example.music_app.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object PlaybackLauncher {

    private var isOpeningPlayer = false
    private val streamResolver = LegacyCatalogStreamResolver()

    fun openPlayer(
        fragment: Fragment,
        song: Song,
        playlist: List<Song> = emptyList(),
        playlistId: String = "",
        playlistName: String = ""
    ) {
        if (isOpeningPlayer) return
        isOpeningPlayer = true

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                val playableQueue = withContext(Dispatchers.IO) {
                    (playlist.ifEmpty { listOf(song) })
                        .filterNot(Song::isDeleted)
                        .mapNotNull { item ->
                            runCatching { streamResolver.resolve(item) }
                                .getOrNull()
                                ?.takeIf { resolved ->
                                    resolved.id.isNotBlank() && resolved.songUrl.isNotBlank()
                                }
                        }
                        .distinctBy(Song::id)
                }

                val playableSong = playableQueue.firstOrNull { item -> item.id == song.id }
                if (playableSong == null) {
                    showPlaybackUnavailable(fragment)
                    return@launch
                }

                (fragment.activity as? MainActivity)?.startMusicService()
                PlayerManager.playPlaylist(
                    songs = playableQueue,
                    startSong = playableSong,
                    context = PlaybackContext(playlistId = playlistId, playlistName = playlistName)
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
}
