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
import com.example.music_app.main.MainActivity

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

                val playableQueue =
                    if (playlist.size > 1) {
                        preparePlayablePlaylistQueue(
                            repository = repository,
                            playlist = playlist,
                            currentPlayableSong = playableSong
                        )
                    } else {
                        prepareRandomQueue(
                            repository = repository,
                            currentPlayableSong = playableSong
                        )
                    }

                (fragment.activity as? MainActivity)?.startMusicService()

                if (playableQueue.size > 1) {
                    PlayerManager.playPlaylist(
                        songs = playableQueue,
                        startSong = playableSong
                    )
                } else {
                    PlayerManager.play(playableSong)
                }

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

    private suspend fun preparePlayablePlaylistForNotification(
        repository: MusicInteractionRepository,
        playlist: List<Song>,
        currentPlayableSong: Song
    ): List<Song> = withContext(Dispatchers.IO) {
        if (playlist.isEmpty()) {
            return@withContext listOf(currentPlayableSong)
        }

        val currentIndex = playlist.indexOfFirst { it.id == currentPlayableSong.id }

        val selectedSongs =
            if (currentIndex >= 0 && playlist.size > 7) {
                val start = (currentIndex - 3).coerceAtLeast(0)
                val end = (start + 7).coerceAtMost(playlist.size)
                playlist.subList(start, end)
            } else {
                playlist.take(7)
            }

        selectedSongs.mapNotNull { item ->
            if (item.id == currentPlayableSong.id) {
                currentPlayableSong
            } else {
                runCatching {
                    if (item.songUrl.isBlank() || item.isSoundCloudSong()) {
                        repository.preparePlayableSong(item)
                    } else {
                        item
                    }
                }.getOrNull()
            }
        }
            .filter { it.id.isNotBlank() }
            .filter { it.songUrl.isNotBlank() }
            .distinctBy { it.id }
    }

    private suspend fun preparePlayablePlaylistQueue(
        repository: MusicInteractionRepository,
        playlist: List<Song>,
        currentPlayableSong: Song
    ): List<Song> = withContext(Dispatchers.IO) {
        val currentIndex = playlist.indexOfFirst { it.id == currentPlayableSong.id }

        val selectedSongs =
            if (currentIndex >= 0 && playlist.size > 8) {
                val start = (currentIndex - 3).coerceAtLeast(0)
                val end = (start + 8).coerceAtMost(playlist.size)
                playlist.subList(start, end)
            } else {
                playlist.take(8)
            }

        selectedSongs.mapNotNull { item ->
            if (item.id == currentPlayableSong.id) {
                currentPlayableSong
            } else {
                runCatching {
                    if (item.songUrl.isBlank() || item.isSoundCloudSong()) {
                        repository.preparePlayableSong(item)
                    } else {
                        item
                    }
                }.getOrNull()
            }
        }
            .filter { it.id.isNotBlank() }
            .filter { it.songUrl.isNotBlank() }
            .distinctBy { it.id }
    }
    private suspend fun prepareRandomQueue(
        repository: MusicInteractionRepository,
        currentPlayableSong: Song
    ): List<Song> = withContext(Dispatchers.IO) {
        val randomCandidates = PlayerManager.fallbackSongs.value
            .orEmpty()
            .filter { it.id.isNotBlank() }
            .filter { it.id != currentPlayableSong.id }
            .filter { !it.isDeleted }
            .shuffled()
            .take(7)

        val preparedRandomSongs = randomCandidates.mapNotNull { item ->
            runCatching {
                if (item.songUrl.isBlank() || item.isSoundCloudSong()) {
                    repository.preparePlayableSong(item)
                } else {
                    item
                }
            }.getOrNull()
        }
            .filter { it.id.isNotBlank() }
            .filter { it.songUrl.isNotBlank() }
            .distinctBy { it.id }

        listOf(currentPlayableSong) + preparedRandomSongs
    }
}