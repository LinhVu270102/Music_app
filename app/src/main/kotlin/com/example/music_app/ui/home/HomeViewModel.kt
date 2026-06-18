package com.example.music_app.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SoundCloudRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

class HomeViewModel : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val soundCloudRepository = SoundCloudRepository()

    private var isLoadingHome = false
    private var hasLoadedOnce = false

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
        val firstPaintStart = System.currentTimeMillis()

        val cachedData = HomeMemoryCache.data

        if (cachedData != null) {
            _relatedTracks.value = cachedData.relatedTracks
            _moreLike.value = cachedData.moreLike
            _hotForYou.value = cachedData.hotForYou
            _trendingByGenre.value = cachedData.trendingByGenre

            Log.d(
                TAG,
                "Home first paint from cache in ${System.currentTimeMillis() - firstPaintStart} ms"
            )
        } else {
            Log.d(TAG, "Home cache is empty")
        }

        if (isLoadingHome) {
            Log.d(TAG, "Home is already loading, skip duplicate request")
            return
        }

        if (HomeMemoryCache.isValid()) {
            Log.d(TAG, "Home cache valid, skip network")
            return
        }

        Log.d(TAG, "Home cache invalid, refresh network")
        refreshHomeDataInBackground()
    }

    private fun refreshHomeDataInBackground() {
        viewModelScope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()

            try {
                isLoadingHome = true
                _isLoading.postValue(true)

                val homeData = supervisorScope {
                    val relatedDeferred = async {
                        safeSearchTracks("lofi chill", 6)
                    }

                    val moreDeferred = async {
                        safeSearchTracks("pop music", 6)
                    }

                    val hotDeferred = async {
                        safeSearchTracks("trending music", 6)
                    }

                    val trendingDeferred = async {
                        safeSearchTracks("hip hop rap", 8)
                    }

                    val related = relatedDeferred.await()
                    val more = moreDeferred.await()
                    val hot = hotDeferred.await()
                    val trending = trendingDeferred.await()

                    HomeData(
                        relatedTracks = related.take(4),
                        moreLike = more,
                        hotForYou = hot.sortedByDescending { song -> song.plays },
                        trendingByGenre = trending
                    )
                }

                HomeMemoryCache.save(homeData)
                hasLoadedOnce = true

                _relatedTracks.postValue(homeData.relatedTracks)
                _moreLike.postValue(homeData.moreLike)
                _hotForYou.postValue(homeData.hotForYou)
                _trendingByGenre.postValue(homeData.trendingByGenre)

                Log.d(
                    TAG,
                    "Home network refresh loaded in ${System.currentTimeMillis() - start} ms"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Home load failed", e)

                if (HomeMemoryCache.data == null) {
                    _errorMessageResId.postValue(R.string.soundcloud_search_failed)
                }
            } finally {
                _isLoading.postValue(false)
                isLoadingHome = false
            }
        }
    }

    private suspend fun safeSearchTracks(
        query: String,
        limit: Int
    ): List<Song> {
        return try {
            val start = System.currentTimeMillis()

            val result = soundCloudRepository.searchTracks(
                query = query,
                limit = limit
            )

            Log.d(
                TAG,
                "Search '$query' loaded ${result.size} songs in ${System.currentTimeMillis() - start} ms"
            )

            result
        } catch (e: Exception) {
            Log.e(TAG, "Search '$query' failed", e)
            emptyList()
        }
    }

    fun loadTrendingByGenreFast(query: String) {
        if (query.isBlank()) return

        genreCache[query]?.let { cachedSongs ->
            _trendingByGenre.value = cachedSongs
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)

                val songs = safeSearchTracks(query, 8)

                genreCache[query] = songs
                _trendingByGenre.postValue(songs)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}