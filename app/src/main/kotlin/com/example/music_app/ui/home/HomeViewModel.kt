package com.example.music_app.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Builds home rows from the Firestore music catalog, grouped by genre. */
class HomeViewModel : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val songRepository = SongRepository()
    private var isLoadingHome = false
    private var catalogSongs: List<Song> = emptyList()

    private val genreCache = mutableMapOf<String, List<Song>>()

    private val _relatedTracks = MutableLiveData<List<Song>>()
    val relatedTracks: LiveData<List<Song>> = _relatedTracks

    private val _moreLike = MutableLiveData<List<Song>>()
    val moreLike: LiveData<List<Song>> = _moreLike

    private val _hotForYou = MutableLiveData<List<Song>>()
    val hotForYou: LiveData<List<Song>> = _hotForYou

    private val _trendingByGenre = MutableLiveData<List<Song>>()
    val trendingByGenre: LiveData<List<Song>> = _trendingByGenre

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    fun loadHomeDataFast() {
        if (HomeMemoryCache.isValid()) HomeMemoryCache.data?.let { cached ->
            _relatedTracks.value = cached.relatedTracks
            _moreLike.value = cached.moreLike
            _hotForYou.value = cached.hotForYou
            _trendingByGenre.value = cached.trendingByGenre
            return
        }
        refreshHomeDataInBackground()
    }

    fun refreshHomeDataByUser() {
        HomeMemoryCache.clear()
        refreshHomeDataInBackground()
    }

    private fun refreshHomeDataInBackground() {
        if (isLoadingHome) return

        viewModelScope.launch(Dispatchers.IO) {
            isLoadingHome = true
            _isLoading.postValue(true)

            try {
                catalogSongs = songRepository.getAllSongs()
                genreCache.clear()

                val homeData = HomeData(
                    relatedTracks = songsForGenre("chill", "lofi", "acoustic").take(4),
                    moreLike = songsForGenre("pop").take(6),
                    hotForYou = catalogSongs
                        .sortedWith(compareByDescending<Song> { it.plays + it.likes }
                            .thenByDescending(Song::createdAt))
                        .take(6),
                    trendingByGenre = songsForGenre("hip hop", "rap", "hiphop").take(8)
                )

                HomeMemoryCache.save(homeData)
                _relatedTracks.postValue(homeData.relatedTracks)
                _moreLike.postValue(homeData.moreLike)
                _hotForYou.postValue(homeData.hotForYou)
                _trendingByGenre.postValue(homeData.trendingByGenre)
            } catch (error: Exception) {
                Log.e(TAG, "Unable to load Firebase catalog", error)
                _errorMessageResId.postValue(R.string.load_song_failed)
            } finally {
                isLoadingHome = false
                _isLoading.postValue(false)
            }
        }
    }

    fun loadTrendingByGenreFast(genre: String) {
        if (genre.isBlank()) return

        genreCache[genre]?.let(_trendingByGenre::postValue)
            ?: viewModelScope.launch(Dispatchers.IO) {
                _isLoading.postValue(true)
                val songs = songsForGenre(genre).take(8)
                genreCache[genre] = songs
                _trendingByGenre.postValue(songs)
                _isLoading.postValue(false)
            }
    }

    private fun songsForGenre(vararg terms: String): List<Song> {
        val normalizedTerms = terms.map(String::lowercase)
        val matchingSongs = catalogSongs.filter { song ->
            val searchable = listOf(song.genre, song.title, song.artist)
                .plus(song.tags)
                .joinToString(" ")
                .lowercase()
            normalizedTerms.any(searchable::contains)
        }

        return (matchingSongs.ifEmpty { catalogSongs })
            .distinctBy(Song::id)
            .sortedWith(compareByDescending<Song> { it.plays + it.likes }
                .thenByDescending(Song::createdAt))
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}
