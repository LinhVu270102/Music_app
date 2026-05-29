package com.example.music_app.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val repository = SongRepository()

    private val _playlist = MutableLiveData<List<Song>>()
    val playlist: LiveData<List<Song>> = _playlist

    private val _recentSongs = MutableLiveData<List<Song>>()
    val recentSongs: LiveData<List<Song>> = _recentSongs

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

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

            } catch (e: Exception) {
                _errorMessage.value = e.message
                Log.e("HomeViewModel", "Error loading home data: ${e.message}", e)
            }
        }
    }

    fun saveRecentlyPlayed(song: Song) {
        viewModelScope.launch {
            try {
                repository.saveRecentlyPlayed(song)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error saving recently played: ${e.message}", e)
            }
        }
    }
}