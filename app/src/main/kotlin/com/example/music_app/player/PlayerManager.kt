package com.example.music_app.player

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.music_app.data.model.Song

object PlayerManager {

    private var exoPlayer: ExoPlayer? = null

    private val playlist = mutableListOf<Song>()
    private var currentIndex = -1

    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    fun init(context: Context) {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context.applicationContext).build()
        }
    }

    fun setPlaylist(songs: List<Song>) {
        playlist.clear()
        playlist.addAll(songs)
    }

    fun play(song: Song) {
        val index = playlist.indexOfFirst { it.id == song.id }
        currentIndex = index

        playInternal(song)
    }

    fun playById(songId: String) {
        val index = playlist.indexOfFirst { it.id == songId }

        if (index != -1) {
            currentIndex = index
            playInternal(playlist[index])
        }
    }

    fun playNext() {
        if (playlist.isEmpty()) return

        currentIndex = if (currentIndex < playlist.lastIndex) {
            currentIndex + 1
        } else {
            0
        }

        playInternal(playlist[currentIndex])
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return

        currentIndex = if (currentIndex > 0) {
            currentIndex - 1
        } else {
            playlist.lastIndex
        }

        playInternal(playlist[currentIndex])
    }

    private fun playInternal(song: Song) {
        _currentSong.value = song

        val mediaItem = MediaItem.fromUri(song.songUrl)

        exoPlayer?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }

        _isPlaying.value = true
    }

    fun pause() {
        exoPlayer?.pause()
        _isPlaying.value = false
    }

    fun resume() {
        exoPlayer?.play()
        _isPlaying.value = true
    }

    fun toggle() {
        if (isCurrentlyPlaying()) pause() else resume()
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }

    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0L
    }

    fun getDuration(): Long {
        return exoPlayer?.duration?.takeIf { it > 0 } ?: 0L
    }

    fun isCurrentlyPlaying(): Boolean {
        return exoPlayer?.isPlaying ?: false
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        playlist.clear()
        currentIndex = -1
        _currentSong.value = null
        _isPlaying.value = false
    }
}