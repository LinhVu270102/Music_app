package com.example.music_app.ui.player

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

class PlayerViewModel : ViewModel() {

    private val repository = SongRepository()

    private val _song = MutableLiveData<Song?>()
    val song: LiveData<Song?> = _song

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    fun loadSong(songId: String) {
        viewModelScope.launch {
            try {
                _song.value = repository.getSong(songId)
            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
                Log.e("PlayerViewModel", "Error loading song", e)
            } catch (e: Exception) {
                _errorMessageResId.value = R.string.load_song_failed
                Log.e("PlayerViewModel", "Error loading song", e)
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}