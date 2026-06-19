package com.example.music_app.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.SearchResultBundle
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SearchRepository
import com.example.music_app.data.repository.SoundCloudRepository
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {

    private val searchRepository = SearchRepository()
    private val soundCloudRepository = SoundCloudRepository()

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

    fun loadSongs() {
        _searchResults.value = SearchResultBundle()
        _songs.value = emptyList()
    }

    fun searchTracks(query: String) {
        val keyword = query.trim()

        if (keyword.isBlank()) {
            clearSearchResult()
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true

                val result = searchRepository.search(keyword)

                _searchResults.value = result
                _songs.value = result.tracks
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.search_failed
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun prepareSongForPlayback(song: Song) {
        if (isPreparingSong) return

        viewModelScope.launch {
            try {
                isPreparingSong = true
                _isLoading.value = true

                val playableSong =
                    if (song.songUrl.isNotBlank()) {
                        song
                    } else {
                        soundCloudRepository.getPlayableSong(song)
                    }

                if (playableSong.songUrl.isBlank()) {
                    _errorMessageResId.value = R.string.song_url_empty
                } else {
                    _playSongEvent.value = playableSong
                }
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.soundcloud_stream_failed
            } finally {
                _isLoading.value = false
                isPreparingSong = false
            }
        }
    }

    fun clearSearchResult() {
        _searchResults.value = SearchResultBundle()
        _songs.value = emptyList()
    }

    fun donePlaySong() {
        _playSongEvent.value = null
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}