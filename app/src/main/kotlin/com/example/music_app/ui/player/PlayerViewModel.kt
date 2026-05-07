package com.example.music_app.ui.player

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import kotlinx.coroutines.launch

class PlayerViewModel : ViewModel() {

    private val repository = SongRepository()

    private val _song = MutableLiveData<Song?>()
    val song: LiveData<Song?> = _song

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadSong(songId: String) {
        viewModelScope.launch {
            try {
                _song.value = repository.getSong(songId)
            } catch (e: Exception) {
                _errorMessage.value = e.message
                Log.e("PlayerViewModel", "Error loading song: ${e.message}", e)
            }
        }
    }
}