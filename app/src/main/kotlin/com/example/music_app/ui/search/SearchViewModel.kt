package com.example.music_app.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.SearchResultBundle
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SearchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SearchViewModel(
    private val searchRepository: SearchRepository = SearchRepository()
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

    private var isPreparingSong = false
    private var searchJob: Job? = null

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
            } catch (_: Exception) {
                publishError(R.string.playback_failed)
            } finally {
                setLoading(false)
                isPreparingSong = false
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
}
