package com.example.music_app.ui.library

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

class LibraryViewModel : ViewModel() {

    companion object {
        private const val TAG = "LibraryViewModel"
    }

    private val repository = SongRepository()

    private val _recentlyPlayed = MutableLiveData<List<Song>>()
    val recentlyPlayed: LiveData<List<Song>> = _recentlyPlayed

    private val _playlists = MutableLiveData<List<Playlist>>()
    val playlists: LiveData<List<Playlist>> = _playlists

    private val _navigateEvent = MutableLiveData<String?>()
    val navigateEvent: LiveData<String?> = _navigateEvent

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    fun loadLibraryData() {
        loadRecentlyPlayed()
        loadMyPlaylists()
    }

    fun loadRecentlyPlayed() {
        viewModelScope.launch {
            try {
                _recentlyPlayed.value = repository.getRecentlyPlayedSongs()
            } catch (e: AppException) {
                Log.e(TAG, "Load recently played failed", e)

                // Không hiện lỗi nếu chưa có data, chỉ để danh sách rỗng.
                _recentlyPlayed.value = emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Load recently played failed", e)

                // Không dùng load_search_data_failed ở Library.
                _recentlyPlayed.value = emptyList()
            }
        }
    }

    fun loadMyPlaylists() {
        viewModelScope.launch {
            try {
                _playlists.value = repository.getMyPlaylists()
            } catch (e: AppException) {
                Log.e(TAG, "Load playlists failed", e)

                // Nếu chưa có playlist thì để rỗng, không cần Toast.
                _playlists.value = emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Load playlists failed", e)

                _playlists.value = emptyList()
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}