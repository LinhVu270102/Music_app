package com.example.music_app.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.MusicInteractionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object PlayerManager {

    private const val TAG = "PlayerManager"

    enum class LoopMode {
        OFF,
        PLAYLIST,
        ONE
    }

    private var exoPlayer: ExoPlayer? = null

    private val playbackQueue = PlaybackQueue()

    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val musicInteractionRepository = MusicInteractionRepository()
    private val historyRecorder = PlaybackHistoryRecorder(
        repository = musicInteractionRepository,
        scope = playerScope
    )
    private var isPreparingRandomSong = false
    private var isExtendingRandomTail = false

    private const val RANDOM_TAIL_SIZE = 5
    private const val MIN_REMAINING_BEFORE_EXTEND = 3

    private val _playlistSongs = MutableLiveData<List<Song>>(emptyList())
    val playlistSongs: LiveData<List<Song>> = _playlistSongs

    private val _playbackContext = MutableLiveData<PlaybackContext?>(null)
    val playbackContext: LiveData<PlaybackContext?> = _playbackContext

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

    private val _fallbackSongs = MutableLiveData<List<Song>>(emptyList())
    val fallbackSongs: LiveData<List<Song>> = _fallbackSongs

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
                        if (playbackState == Player.STATE_READY) {
                            Log.d(
                                TAG,
                                "STATE_READY duration=${exoPlayer?.duration}, seekable=${exoPlayer?.isCurrentMediaItemSeekable}"
                            )
                        }

                        // ExoPlayer owns automatic transitions. Calling our own
                        // next-song routine here used to override repeat-one,
                        // repeat-playlist and shuffle at the end of a track.
                    }

                    override fun onMediaItemTransition(
                        mediaItem: MediaItem?,
                        reason: Int
                    ) {
                        val songId = mediaItem?.mediaId ?: return
                        val index = playbackQueue.indexOf(songId)
                        val song = playbackQueue.select(index)

                        if (song != null) {
                            _currentIndex.value = index
                            _currentSong.value = song
                            historyRecorder.record(song)

                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "Player error: ${error.errorCodeName}", error)

                        _isPlaying.value = false

                        val httpError = findHttpError(error)

                        _errorMessageResId.value =
                            R.string.playback_failed
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

    fun setPlaylist(songs: List<Song>) {
        val validSongs = playbackQueue.replace(songs)

        _playlistSongs.value = validSongs

        val mediaItems = validSongs.map(PlaybackMediaItemFactory::create)

        exoPlayer?.apply {
            stop()
            clearMediaItems()
            setMediaItems(mediaItems, false)
            prepare()
        }

        _currentIndex.value = -1
        _playbackContext.value = null

        applyShuffleMode()
        applyLoopMode()

        Log.d(TAG, "setPlaylist: ${validSongs.size} valid songs")
    }

    fun playPlaylist(
        songs: List<Song>,
        startSong: Song,
        context: PlaybackContext? = null
    ) {
        val validSongs = songs
            .filter { song -> song.id.isNotBlank() && song.songUrl.isNotBlank() }
            .distinctBy(Song::id)

        if (validSongs.isEmpty()) {
            Log.e(TAG, "playPlaylist failed: no valid songs")
            _errorMessageResId.value = R.string.playback_failed
            return
        }

        playbackQueue.replace(validSongs)

        _playlistSongs.value = validSongs
        _playbackContext.value = context?.takeIf { playbackContext ->
            playbackContext.isPlaylist
        }

        val mediaItems = validSongs.map(PlaybackMediaItemFactory::create)

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

        val index = playbackQueue.indexOf(song.id)

        if (index != -1 && playbackQueue.size > 1) {
            playFromPlaylist(index)
        } else {
            playSingle(song)
        }
    }

    private fun playFromPlaylist(index: Int) {
        val song = playbackQueue.select(index)

        if (song == null) {
            Log.e(TAG, "playFromPlaylist failed: song null at index=$index")
            _errorMessageResId.value = R.string.playback_failed
            return
        }

        _currentIndex.value = index
        _currentSong.value = song
        historyRecorder.record(song)

        exoPlayer?.apply {
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
        val singleSongPlaylist = playbackQueue.replaceWithSingle(song)
        _currentIndex.value = 0
        _playlistSongs.value = singleSongPlaylist
        _playbackContext.value = null
        _currentSong.value = song
        historyRecorder.record(song)

        val mediaItem = PlaybackMediaItemFactory.create(song)

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
        if (index !in playbackQueue.indices) {
            Log.d(TAG, "playSongAt failed: invalid index=$index, size=${playbackQueue.size}")
            return
        }

        playFromPlaylist(index)
    }

    fun playNext() {
        val player = exoPlayer ?: return

        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.play()
        } else if (_loopMode.value == LoopMode.PLAYLIST && player.mediaItemCount > 0) {
            player.seekToDefaultPosition(0)
            player.play()
        }
    }

    fun playPrevious() {
        val player = exoPlayer ?: return

        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
            player.play()
        } else if (_loopMode.value == LoopMode.PLAYLIST && player.mediaItemCount > 0) {
            player.seekToDefaultPosition(player.mediaItemCount - 1)
            player.play()
        } else {
            player.seekTo(0L)
        }
    }

    fun peekNextSong(): Song? {
        val player = exoPlayer ?: return null
        val nextIndex = player.nextMediaItemIndex
            .takeIf { it != C.INDEX_UNSET }
            ?: if (_loopMode.value == LoopMode.PLAYLIST && player.mediaItemCount > 0) 0 else C.INDEX_UNSET
        return peekQueueSong(nextIndex)
    }

    fun peekPreviousSong(): Song? {
        val player = exoPlayer ?: return null
        val previousIndex = player.previousMediaItemIndex
            .takeIf { it != C.INDEX_UNSET }
            ?: if (_loopMode.value == LoopMode.PLAYLIST && player.mediaItemCount > 0) {
                player.mediaItemCount - 1
            } else {
                C.INDEX_UNSET
            }
        return peekQueueSong(previousIndex)
    }

    private fun peekQueueSong(mediaIndex: Int): Song? {
        val player = exoPlayer ?: return null
        if (mediaIndex == C.INDEX_UNSET || mediaIndex !in 0 until player.mediaItemCount) return null

        val mediaId = player.getMediaItemAt(mediaIndex).mediaId
        return playbackQueue.snapshot().firstOrNull { song -> song.id == mediaId }
    }

    private fun extendRandomTailIfNeeded() {
        val player = exoPlayer ?: return
        val currentIndex = _currentIndex.value ?: -1
        val currentSongs = _playlistSongs.value.orEmpty()

        if (isExtendingRandomTail) return
        if (currentIndex < 0) return
        if (currentSongs.isEmpty()) return

        val remainingSongs = currentSongs.lastIndex - currentIndex

        if (remainingSongs > MIN_REMAINING_BEFORE_EXTEND) return

        val currentIds = currentSongs.map { it.id }.toSet()

        val candidates = PlaybackSongSelector.selectRandomTail(
            songs = _fallbackSongs.value.orEmpty(),
            queuedSongIds = currentIds,
            limit = RANDOM_TAIL_SIZE
        )

        if (candidates.isEmpty()) {
            Log.d(TAG, "extendRandomTailIfNeeded: no random candidates")
            return
        }

        isExtendingRandomTail = true

        playerScope.launch {
            try {
                val preparedSongs = withContext(Dispatchers.IO) {
                    candidates.mapNotNull { song ->
                        runCatching {
                            if (song.songUrl.isBlank()) {
                                musicInteractionRepository.preparePlayableSong(song)
                            } else {
                                song
                            }
                        }.getOrNull()
                    }
                        .filter { it.id.isNotBlank() }
                        .filter { it.songUrl.isNotBlank() }
                        .distinctBy { it.id }
                }

                if (preparedSongs.isEmpty()) {
                    Log.d(TAG, "extendRandomTailIfNeeded: prepared songs empty")
                    return@launch
                }

                val additions = playbackQueue.append(preparedSongs)
                if (additions.isEmpty()) {
                    Log.d(TAG, "extendRandomTailIfNeeded: no unique prepared songs")
                    return@launch
                }

                val mediaItems = additions.map(PlaybackMediaItemFactory::create)

                player.addMediaItems(mediaItems)

                _playlistSongs.value = playbackQueue.snapshot()

                Log.d(
                    TAG,
                    "extendRandomTailIfNeeded: added=${additions.size}, total=${playbackQueue.size}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "extendRandomTailIfNeeded failed: ${e.message}", e)
            } finally {
                isExtendingRandomTail = false
            }
        }
    }
    fun setFallbackSongs(songs: List<Song>) {
        val validSongs = PlaybackSongSelector.sanitizeFallbackSongs(songs)

        if (validSongs.isEmpty()) {
            Log.d(TAG, "setFallbackSongs ignored empty list")
            return
        }

        val oldIds = _fallbackSongs.value.orEmpty().map { song -> song.id }
        val newIds = validSongs.map { song -> song.id }

        if (oldIds == newIds) {
            Log.d(TAG, "setFallbackSongs ignored same list: ${validSongs.size}")
            return
        }

        _fallbackSongs.value = validSongs

        Log.d(TAG, "setFallbackSongs: ${validSongs.size}")
    }
    private fun playRandomFallback(excludeIds: Set<String> = emptySet()) {
        if (isPreparingRandomSong) return

        val currentId = _currentSong.value?.id.orEmpty()

        val randomSong = PlaybackSongSelector.selectRandomFallback(
            songs = _fallbackSongs.value.orEmpty(),
            currentSongId = currentId,
            excludedSongIds = excludeIds
        )
        if (randomSong == null) {
            _errorMessageResId.postValue(R.string.could_not_play_this_song)
            return
        }

        playPreparedSong(randomSong)
    }
    fun playPreparedSong(song: Song) {
        if (isPreparingRandomSong) {
            Log.d(TAG, "playPreparedSong ignored: already preparing")
            return
        }

        isPreparingRandomSong = true

        playerScope.launch {
            try {
                val playableSong = withContext(Dispatchers.IO) {
                    musicInteractionRepository.preparePlayableSong(song)
                }

                if (playableSong.songUrl.isBlank()) {
                    Log.e(TAG, "playPreparedSong failed: songUrl blank")
                    _errorMessageResId.value = R.string.song_url_empty
                    return@launch
                }

                play(playableSong)
            } catch (e: Exception) {
                Log.e(TAG, "playPreparedSong failed: ${e.message}", e)
                _errorMessageResId.value = R.string.playback_failed
            } finally {
                isPreparingRandomSong = false
            }
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

        playbackQueue.clear()

        _playlistSongs.value = emptyList()
        _playbackContext.value = null
        _currentIndex.value = -1
        _currentSong.value = null
        _isPlaying.value = false
        historyRecorder.clear()
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null

        playbackQueue.clear()

        _playlistSongs.value = emptyList()
        _playbackContext.value = null
        _currentIndex.value = -1
        _currentSong.value = null
        _isPlaying.value = false
        _isShuffleEnabled.value = false
        _loopMode.value = LoopMode.OFF
        _fallbackSongs.value = emptyList()
        historyRecorder.clear()
    }

    fun getCurrentPlaylist(): List<Song> {
        return playbackQueue.snapshot()
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}
