package com.example.music_app.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SoundCloudRepository
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {

    private val soundCloudRepository = SoundCloudRepository()

    private val _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> = _songs

    private val _playSongEvent = MutableLiveData<Song?>()
    val playSongEvent: LiveData<Song?> = _playSongEvent

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    /**
     * Giữ lại hàm này để SearchFragment cũ không bị lỗi.
     * Trước đây loadSongs() lấy toàn bộ bài từ Firebase.
     * Bây giờ Search dùng SoundCloud API nên ban đầu để danh sách rỗng.
     */
    fun loadSongs() {
        _songs.value = emptyList()
    }

    fun searchTracks(query: String) {
        val keyword = query.trim()

        if (keyword.isBlank()) {
            _songs.value = emptyList()
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true

                val result = soundCloudRepository.searchTracks(
                    query = keyword,
                    limit = 20
                )

                _songs.value = result
            } catch (e: Exception) {
                _errorMessageResId.value = R.string.soundcloud_search_failed
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun prepareSongForPlayback(song: Song) {
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                _errorMessageResId.value = R.string.soundcloud_stream_failed
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearSearchResult() {
        _songs.value = emptyList()
    }

    fun donePlaySong() {
        _playSongEvent.value = null
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}