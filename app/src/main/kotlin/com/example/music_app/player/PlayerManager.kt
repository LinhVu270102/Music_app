package com.example.music_app.player

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
        if (exoPlayer != null) {
            applyShuffleMode()
            applyLoopMode()
            return
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        exoPlayer = ExoPlayer.Builder(context.applicationContext)
            .build()
            .apply {
                setAudioAttributes(audioAttributes, true)
                playWhenReady = false

                addListener(object : Player.Listener {

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
        val validSongs = songs.filter { it.songUrl.isNotBlank() }

        playlist.clear()
        playlist.addAll(validSongs)

        val mediaItems = validSongs.map { song ->
            MediaItem.Builder()
                .setUri(song.songUrl)
                .setMediaId(song.id)
                .build()
        }

        exoPlayer?.apply {
            stop()
            clearMediaItems()
            setMediaItems(mediaItems, false)
            prepare()
        }

        currentIndex = -1

        applyShuffleMode()
        applyLoopMode()
    }

    fun play(song: Song) {
        if (song.songUrl.isBlank()) {
            return
        }

        val current = _currentSong.value

        if (
            current?.id == song.id &&
            current.songUrl == song.songUrl &&
            exoPlayer?.isPlaying == true
        ) {
            return
        }

        val index = playlist.indexOfFirst { it.id == song.id }

        if (index != -1) {
            playFromPlaylist(index)
        } else {
            playSingle(song)
        }
    }

    private fun playFromPlaylist(index: Int) {
        val song = playlist.getOrNull(index) ?: return

        currentIndex = index
        _currentSong.value = song

        exoPlayer?.apply {
            stop()
            seekTo(index, 0L)
            setPlaybackSpeed(1f)
            volume = 1f
            prepare()
            play()
        }

        _isPlaying.value = true
    }

    private fun playSingle(song: Song) {
        currentIndex = -1
        _currentSong.value = song

        val mediaItem = MediaItem.Builder()
            .setUri(song.songUrl)
            .setMediaId(song.id)
            .build()

        exoPlayer?.apply {
            stop()
            clearMediaItems()
            setPlaybackSpeed(1f)
            volume = 1f
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
        exoPlayer?.apply {
            stop()
            clearMediaItems()
        }

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