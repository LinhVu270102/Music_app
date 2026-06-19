package com.example.music_app.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.example.music_app.R
import com.example.music_app.data.model.Song

object PlayerManager {

    private const val TAG = "PlayerManager"

    enum class LoopMode {
        OFF,
        PLAYLIST,
        ONE
    }

    private var exoPlayer: ExoPlayer? = null

    private val playlist = mutableListOf<Song>()
    private var currentPlaylistIndex = -1

    private val _playlistSongs = MutableLiveData<List<Song>>(emptyList())
    val playlistSongs: LiveData<List<Song>> = _playlistSongs

    private val _currentIndex = MutableLiveData(-1)
    val currentIndex: LiveData<Int> = _currentIndex

    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _isShuffleEnabled = MutableLiveData(false)
    val isShuffleEnabled: LiveData<Boolean> = _isShuffleEnabled

    private val _loopMode = MutableLiveData(LoopMode.OFF)
    val loopMode: LiveData<LoopMode> = _loopMode

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

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
                        Log.d(TAG, "onIsPlayingChanged: $isPlaying")
                        _isPlaying.value = isPlaying
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        Log.d(TAG, "onPlaybackStateChanged: $playbackState")

                        if (playbackState == Player.STATE_ENDED) {
                            _isPlaying.value = false

                            if (_loopMode.value == LoopMode.OFF) {
                                currentPlaylistIndex = -1
                                _currentIndex.value = -1
                                _currentSong.value = null
                            }
                        }
                    }

                    override fun onMediaItemTransition(
                        mediaItem: MediaItem?,
                        reason: Int
                    ) {
                        val songId = mediaItem?.mediaId ?: return
                        val index = playlist.indexOfFirst { it.id == songId }
                        val song = playlist.getOrNull(index)

                        if (song != null) {
                            currentPlaylistIndex = index
                            _currentIndex.value = index
                            _currentSong.value = song
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "Player error: ${error.errorCodeName}", error)

                        _isPlaying.value = false

                        val httpError = findHttpError(error)

                        _errorMessageResId.value =
                            when (httpError?.responseCode) {
                                429 -> R.string.soundcloud_rate_limited
                                else -> R.string.playback_failed
                            }
                    }
                })
            }

        applyShuffleMode()
        applyLoopMode()
    }

    private fun findHttpError(
        throwable: Throwable?
    ): HttpDataSource.InvalidResponseCodeException? {
        var current = throwable

        while (current != null) {
            if (current is HttpDataSource.InvalidResponseCodeException) {
                return current
            }

            current = current.cause
        }

        return null
    }

    private fun createMediaItem(song: Song): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(song.songUrl)
            .setMediaId(song.id)

        if (
            song.tags.contains("hls") ||
            song.songUrl.contains(".m3u8", ignoreCase = true) ||
            song.songUrl.contains("/hls", ignoreCase = true)
        ) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }

        return builder.build()
    }

    fun setPlaylist(songs: List<Song>) {
        val validSongs = songs
            .filter { it.songUrl.isNotBlank() }
            .distinctBy { it.id }

        playlist.clear()
        playlist.addAll(validSongs)

        _playlistSongs.value = validSongs

        val mediaItems = validSongs.map { song ->
            createMediaItem(song)
        }

        exoPlayer?.apply {
            stop()
            clearMediaItems()
            setMediaItems(mediaItems, false)
            prepare()
        }

        currentPlaylistIndex = -1
        _currentIndex.value = -1

        applyShuffleMode()
        applyLoopMode()

        Log.d(TAG, "setPlaylist: ${validSongs.size} valid songs")
    }

    fun playPlaylist(
        songs: List<Song>,
        startSong: Song
    ) {
        val validSongs = songs
            .filter { it.songUrl.isNotBlank() }
            .distinctBy { it.id }

        if (validSongs.isEmpty()) {
            _errorMessageResId.value = R.string.playback_failed
            return
        }

        playlist.clear()
        playlist.addAll(validSongs)

        _playlistSongs.value = validSongs

        val mediaItems = validSongs.map { song ->
            createMediaItem(song)
        }

        exoPlayer?.apply {
            stop()
            clearMediaItems()
            setMediaItems(mediaItems, false)
            prepare()
        }

        val startIndex = validSongs.indexOfFirst { it.id == startSong.id }
            .takeIf { it >= 0 }
            ?: 0

        playFromPlaylist(startIndex)

        applyShuffleMode()
        applyLoopMode()

        Log.d(TAG, "playPlaylist: size=${validSongs.size}, startIndex=$startIndex")
    }

    fun play(song: Song) {
        Log.d(TAG, "play called: id=${song.id}, title=${song.title}, url=${song.songUrl}")

        if (song.songUrl.isBlank()) {
            Log.e(TAG, "play failed: songUrl is blank")
            _errorMessageResId.value = R.string.song_url_empty
            return
        }

        if (exoPlayer == null) {
            Log.e(TAG, "play failed: exoPlayer is null")
            _errorMessageResId.value = R.string.player_not_ready
            return
        }

        val current = _currentSong.value

        if (
            current?.id == song.id &&
            current.songUrl == song.songUrl &&
            isCurrentlyPlaying()
        ) {
            Log.d(TAG, "same song is already playing, ignore")
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
        val song = playlist.getOrNull(index)

        if (song == null) {
            Log.e(TAG, "playFromPlaylist failed: song null at index=$index")
            _errorMessageResId.value = R.string.playback_failed
            return
        }

        currentPlaylistIndex = index
        _currentIndex.value = index
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

        Log.d(TAG, "playFromPlaylist: index=$index, title=${song.title}")
    }

    private fun playSingle(song: Song) {
        playlist.clear()
        playlist.add(song)

        currentPlaylistIndex = 0
        _currentIndex.value = 0
        _playlistSongs.value = listOf(song)
        _currentSong.value = song

        val mediaItem = createMediaItem(song)

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

        Log.d(TAG, "playSingle: ${song.title}")
    }

    fun playSongAt(index: Int) {
        if (index !in playlist.indices) {
            Log.d(TAG, "playSongAt failed: invalid index=$index, size=${playlist.size}")
            return
        }

        playFromPlaylist(index)
    }

    fun playNext() {
        if (playlist.isEmpty()) return

        val nextIndex = currentPlaylistIndex + 1

        if (nextIndex in playlist.indices) {
            playSongAt(nextIndex)
        } else {
            playSongAt(0)
        }
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return

        val previousIndex = currentPlaylistIndex - 1

        if (previousIndex in playlist.indices) {
            playSongAt(previousIndex)
        } else {
            playSongAt(playlist.lastIndex)
        }
    }

    fun pause() {
        exoPlayer?.pause()
        _isPlaying.value = false
    }

    fun resume() {
        val player = exoPlayer

        if (player == null) {
            _errorMessageResId.value = R.string.player_not_ready
            return
        }

        player.play()
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

        currentPlaylistIndex = -1
        _currentIndex.value = -1
        _currentSong.value = null
        _isPlaying.value = false
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null

        playlist.clear()
        currentPlaylistIndex = -1

        _playlistSongs.value = emptyList()
        _currentIndex.value = -1
        _currentSong.value = null
        _isPlaying.value = false
        _isShuffleEnabled.value = false
        _loopMode.value = LoopMode.OFF
    }

    fun getCurrentPlaylist(): List<Song> {
        return playlist.toList()
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}