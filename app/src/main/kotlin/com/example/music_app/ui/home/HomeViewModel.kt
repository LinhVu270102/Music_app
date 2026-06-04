package com.example.music_app.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val repository = SongRepository()

    private val _playlist = MutableLiveData<List<Song>>()
    val playlist: LiveData<List<Song>> = _playlist

    private val _recentSongs = MutableLiveData<List<Song>>()
    val recentSongs: LiveData<List<Song>> = _recentSongs

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    fun loadHomeData() {
        viewModelScope.launch {
            try {
                val allSongs = repository.getAllSongs()
                val recentlyPlayed = repository.getRecentlyPlayedSongs()

                _playlist.value = allSongs

                _recentSongs.value = if (recentlyPlayed.isNotEmpty()) {
                    recentlyPlayed
                } else {
                    allSongs.take(4)
                }

            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
                Log.e("HomeViewModel", "Error loading home data", e)
            } catch (e: Exception) {
                _errorMessageResId.value = R.string.load_home_data_failed
                Log.e("HomeViewModel", "Error loading home data", e)
            }
        }
    }

    fun saveRecentlyPlayed(song: Song) {
        viewModelScope.launch {
            try {
                repository.saveRecentlyPlayed(song)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error saving recently played", e)
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}