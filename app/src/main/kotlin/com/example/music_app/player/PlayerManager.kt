package com.example.music_app.player

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.music_app.data.model.Song

object PlayerManager {

    enum class LoopMode {
        OFF,
        PLAYLIST,
        ONE
    }

    private var exoPlayer: ExoPlayer? = null

    private val playlist = mutableListOf<Song>()
    private var currentIndex = -1

    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _isShuffleEnabled = MutableLiveData(false)
    val isShuffleEnabled: LiveData<Boolean> = _isShuffleEnabled

    private val _loopMode = MutableLiveData(LoopMode.OFF)
    val loopMode: LiveData<LoopMode> = _loopMode

    fun init(context: Context) {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context.applicationContext).build()

            exoPlayer?.addListener(object : Player.Listener {

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val songId = mediaItem?.mediaId ?: return
                    val song = playlist.find { it.id == songId }

                    song?.let {
                        currentIndex = playlist.indexOfFirst { item -> item.id == songId }
                        _currentSong.value = it
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        _isPlaying.value = false

                        if (_loopMode.value == LoopMode.OFF) {
                            currentIndex = -1
                            _currentSong.value = null
                        }
                    }
                }
            })
        }

        applyShuffleMode()
        applyLoopMode()
    }

    fun setPlaylist(songs: List<Song>) {
        playlist.clear()
        playlist.addAll(songs)

        val mediaItems = songs.map { song ->
            MediaItem.Builder()
                .setUri(song.songUrl)
                .setMediaId(song.id)
                .build()
        }

        exoPlayer?.setMediaItems(mediaItems, false)
        exoPlayer?.prepare()

        applyShuffleMode()
        applyLoopMode()
    }

    fun play(song: Song) {
        val index = playlist.indexOfFirst { it.id == song.id }

        if (index != -1) {
            currentIndex = index
            _currentSong.value = playlist[index]

            exoPlayer?.seekTo(index, 0L)
            exoPlayer?.prepare()
            exoPlayer?.play()

            _isPlaying.value = true
        } else {
            playSingle(song)
        }
    }

    private fun playSingle(song: Song) {
        currentIndex = -1
        _currentSong.value = song

        val mediaItem = MediaItem.Builder()
            .setUri(song.songUrl)
            .setMediaId(song.id)
            .build()

        exoPlayer?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }

        _isPlaying.value = true
    }

    fun playNext() {
        exoPlayer?.seekToNextMediaItem()
    }

    fun playPrevious() {
        exoPlayer?.seekToPreviousMediaItem()
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
        if (isCurrentlyPlaying()) {
            pause()
        } else {
            resume()
        }
    }

    fun toggleShuffle() {
        val newValue = !(_isShuffleEnabled.value ?: false)
        _isShuffleEnabled.value = newValue
        applyShuffleMode()
    }

    fun toggleLoopMode() {
        val nextMode = when (_loopMode.value ?: LoopMode.OFF) {
            LoopMode.OFF -> LoopMode.PLAYLIST
            LoopMode.PLAYLIST -> LoopMode.ONE
            LoopMode.ONE -> LoopMode.OFF
        }

        _loopMode.value = nextMode
        applyLoopMode()
    }

    private fun applyShuffleMode() {
        exoPlayer?.shuffleModeEnabled = _isShuffleEnabled.value == true
    }

    private fun applyLoopMode() {
        exoPlayer?.repeatMode = when (_loopMode.value ?: LoopMode.OFF) {
            LoopMode.OFF -> Player.REPEAT_MODE_OFF
            LoopMode.PLAYLIST -> Player.REPEAT_MODE_ALL
            LoopMode.ONE -> Player.REPEAT_MODE_ONE
        }
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

    fun getPlayer(): ExoPlayer? {
        return exoPlayer
    }

    fun stop() {
        exoPlayer?.stop()
        currentIndex = -1
        _currentSong.value = null
        _isPlaying.value = false
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null

        playlist.clear()
        currentIndex = -1

        _currentSong.value = null
        _isPlaying.value = false
        _isShuffleEnabled.value = false
        _loopMode.value = LoopMode.OFF
    }
    fun getCurrentPlaylist(): List<Song> {
        return playlist.toList()
    }
}