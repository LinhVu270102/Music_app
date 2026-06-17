package com.example.music_app.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.remote.FirebaseService
import com.example.music_app.data.repository.SoundCloudRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val soundCloudRepository = SoundCloudRepository()
    private val firebaseService = FirebaseService(FirebaseFirestore.getInstance())

    private var isPreparingSong = false

    private val _relatedTracks = MutableLiveData<List<Song>>()
    val relatedTracks: LiveData<List<Song>> = _relatedTracks

    private val _moreLike = MutableLiveData<List<Song>>()
    val moreLike: LiveData<List<Song>> = _moreLike

    private val _hotForYou = MutableLiveData<List<Song>>()
    val hotForYou: LiveData<List<Song>> = _hotForYou

    private val _trendingByGenre = MutableLiveData<List<Song>>()
    val trendingByGenre: LiveData<List<Song>> = _trendingByGenre

    private val _playSongEvent = MutableLiveData<Song?>()
    val playSongEvent: LiveData<Song?> = _playSongEvent

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    fun loadHomeData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)

                val related = soundCloudRepository.searchTracks(
                    query = "lofi chill",
                    limit = 10
                )

                val more = soundCloudRepository.searchTracks(
                    query = "pop music",
                    limit = 10
                )

                val hot = soundCloudRepository.searchTracks(
                    query = "trending music",
                    limit = 10
                )

                val trending = soundCloudRepository.searchTracks(
                    query = "hip hop rap",
                    limit = 12
                )

                _relatedTracks.postValue(related.take(4))
                _moreLike.postValue(more)
                _hotForYou.postValue(hot.sortedByDescending { it.plays })
                _trendingByGenre.postValue(trending)
            } catch (e: Exception) {
                _errorMessageResId.postValue(R.string.soundcloud_search_failed)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun loadTrendingByGenre(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)

                val songs = soundCloudRepository.searchTracks(
                    query = query,
                    limit = 12
                )

                _trendingByGenre.postValue(songs)
            } catch (e: Exception) {
                _errorMessageResId.postValue(R.string.soundcloud_search_failed)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun prepareSongForPlayback(song: Song) {
        if (isPreparingSong) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                isPreparingSong = true
                _isLoading.postValue(true)

                val playableSong =
                    if (song.songUrl.isNotBlank()) {
                        song
                    } else {
                        soundCloudRepository.getPlayableSong(song)
                    }

                if (playableSong.songUrl.isBlank()) {
                    _errorMessageResId.postValue(R.string.song_url_empty)
                } else {
                    _playSongEvent.postValue(playableSong)
                }
            } catch (e: Exception) {
                _errorMessageResId.postValue(R.string.soundcloud_stream_failed)
            } finally {
                _isLoading.postValue(false)
                isPreparingSong = false
            }
        }
    }

    fun saveRecentlyPlayed(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch

                firebaseService.upsertSong(song)
                firebaseService.saveRecentlyPlayed(userId, song.id)
            } catch (_: Exception) {
            }
        }
    }

    fun donePlaySong() {
        _playSongEvent.value = null
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}