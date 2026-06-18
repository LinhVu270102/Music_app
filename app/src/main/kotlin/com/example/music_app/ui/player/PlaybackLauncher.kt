package com.example.music_app.ui.player

import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.remote.soundcloud.isSoundCloudSong
import com.example.music_app.data.repository.MusicInteractionRepository
import com.example.music_app.player.PlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object PlaybackLauncher {

    private var isOpeningPlayer = false

    fun openPlayer(
        fragment: Fragment,
        song: Song,
        playlist: List<Song> = emptyList()
    ) {
        if (isOpeningPlayer) return

        isOpeningPlayer = true

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                val repository = MusicInteractionRepository()

                val playableSong = withContext(Dispatchers.IO) {
                    repository.preparePlayableSongAndSaveRecently(song)
                }

                if (playableSong.songUrl.isBlank()) {
                    Toast.makeText(
                        fragment.requireContext(),
                        fragment.getString(R.string.song_url_empty),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val canUsePlaylist =
                    playlist.isNotEmpty() &&
                            playlist.none { it.isSoundCloudSong() } &&
                            playlist.all { it.songUrl.isNotBlank() }

                if (canUsePlaylist) {
                    PlayerManager.setPlaylist(playlist)
                }

                PlayerManager.play(playableSong)

                fragment.parentFragmentManager.commit {
                    replace(
                        R.id.fragmentContainer,
                        PlayerFragment.newInstance(playableSong.id)
                    )
                    addToBackStack(null)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    fragment.requireContext(),
                    fragment.getString(R.string.soundcloud_stream_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isOpeningPlayer = false
            }
        }
    }
}