package com.example.music_app.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.SearchResultBundle
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.AudioSearchRepository
import com.example.music_app.data.repository.SearchRepository
import com.example.music_app.ui.search.state.AudioSearchUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SearchViewModel(
    private val searchRepository: SearchRepository = SearchRepository(),
    private val audioSearchRepository: AudioSearchRepository = AudioSearchRepository()
) : ViewModel() {

    private val _searchResults = MutableLiveData(SearchResultBundle())
    val searchResults: LiveData<SearchResultBundle> = _searchResults

    /**
     * Giữ lại songs để không phá code cũ nếu nơi khác còn observe.
     */
    private val _songs = MutableLiveData<List<Song>>(emptyList())
    val songs: LiveData<List<Song>> = _songs

    private val _playSongEvent = MutableLiveData<Song?>()
    val playSongEvent: LiveData<Song?> = _playSongEvent

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    private val _audioSearchState = MutableLiveData<AudioSearchUiState>(AudioSearchUiState.Idle)
    val audioSearchState: LiveData<AudioSearchUiState> = _audioSearchState

    private var isPreparingSong = false
    private var searchJob: Job? = null
    private var audioSearchJob: Job? = null

    fun loadSongs() {
        clearSearchResult()
    }

    fun searchTracks(query: String) {
        val keyword = query.trim()

        if (keyword.isBlank()) {
            clearSearchResult()
            return
        }

        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            try {
                setLoading(true)

                publishSearchResult(
                    searchRepository.search(keyword).copy(query = keyword)
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                publishError(R.string.search_failed)
            } finally {
                setLoading(false)
            }
        }
    }

    fun prepareSongForPlayback(song: Song) {
        if (isPreparingSong) return

        viewModelScope.launch {
            try {
                isPreparingSong = true
                setLoading(true)

                if (song.isPlayable()) {
                    _playSongEvent.value = song
                } else {
                    publishError(R.string.song_url_empty)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                publishError(R.string.playback_failed)
            } finally {
                setLoading(false)
                isPreparingSong = false
            }
        }
    }

    fun searchByAudioSample(
        audioBase64: String,
        fileExtension: String
    ) {
        val cleanAudioBase64 = audioBase64.trim()

        if (cleanAudioBase64.isBlank()) {
            _audioSearchState.value = AudioSearchUiState.Error(R.string.audio_search_failed)
            return
        }

        audioSearchJob?.cancel()

        audioSearchJob = viewModelScope.launch {
            try {
                setLoading(true)
                _audioSearchState.value = AudioSearchUiState.Searching

                val songs = audioSearchRepository
                    .searchByAudioSample(
                        audioBase64 = cleanAudioBase64,
                        fileExtension = fileExtension,
                        limit = AUDIO_SEARCH_LIMIT
                    )
                    .mapNotNull { match -> match.song }
                    .filter { song -> song.songUrl.isNotBlank() }
                    .distinctBy { song -> song.id }

                _audioSearchState.value = if (songs.isEmpty()) {
                    AudioSearchUiState.NoMatch
                } else {
                    AudioSearchUiState.Success(songs)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _audioSearchState.value = AudioSearchUiState.Error(R.string.audio_search_failed)
            } finally {
                setLoading(false)
            }
        }
    }

    fun clearSearchResult() {
        publishSearchResult(SearchResultBundle())
    }

    fun donePlaySong() {
        _playSongEvent.value = null
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }

    fun clearAudioSearchState() {
        _audioSearchState.value = AudioSearchUiState.Idle
    }

    fun cancelAudioSearch() {
        audioSearchJob?.cancel()
        audioSearchJob = null
        setLoading(false)
        clearAudioSearchState()
    }

    private fun publishSearchResult(result: SearchResultBundle) {
        _searchResults.value = result
        _songs.value = result.tracks
    }

    private fun publishError(messageResId: Int) {
        _errorMessageResId.value = messageResId
    }

    private fun setLoading(isLoading: Boolean) {
        _isLoading.value = isLoading
    }

    private fun Song.isPlayable(): Boolean {
        return songUrl.isNotBlank()
    }

    private companion object {
        private const val AUDIO_SEARCH_LIMIT = 10
    }
}
