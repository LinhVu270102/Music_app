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
import kotlinx.coroutines.Job

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

    private val _apiArtistTrackCounts = MutableLiveData<ApiArtistTrackCounts?>()
    val apiArtistTrackCounts: LiveData<ApiArtistTrackCounts?> = _apiArtistTrackCounts

    private var isPreparingSong = false
    private var searchJob: Job? = null
    private var artistTrackCountJob: Job? = null
    private val artistTrackCountCache = mutableMapOf<String, Int>()

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

        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            try {
                _isLoading.value = true

                val result = searchRepository.search(keyword)

                val resultWithQuery = result.copy(
                    query = keyword
                )

                _searchResults.value = resultWithQuery
                _songs.value = resultWithQuery.tracks
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

    fun loadApiArtistTrackCounts(
        profiles: List<SearchResultItem.ApiArtistProfile>,
        keyword: String,
        tab: SearchTab
    ) {
        artistTrackCountJob?.cancel()
        if (profiles.isEmpty()) return

        artistTrackCountJob = viewModelScope.launch {
            val counts = buildMap {
                profiles.forEach { profile ->
                    val key = profile.artistName.trim().lowercase()
                    val remoteCount = artistTrackCountCache[key]
                        ?: soundCloudRepository.getArtistTrackCount(profile.artistName).also { count ->
                            if (count > 0) artistTrackCountCache[key] = count
                        }

                    put(key, maxOf(profile.trackCount, remoteCount))
                }
            }

            _apiArtistTrackCounts.value = ApiArtistTrackCounts(
                keyword = keyword,
                tab = tab,
                counts = counts
            )
        }
    }

    fun clearSearchResult() {
        artistTrackCountJob?.cancel()
        artistTrackCountCache.clear()
        _apiArtistTrackCounts.value = null
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

/** Artist counts enriched from SoundCloud for the active search request. */
data class ApiArtistTrackCounts(
    val keyword: String,
    val tab: SearchTab,
    val counts: Map<String, Int>
)
