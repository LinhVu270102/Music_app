package com.example.music_app.ui.yourlikes

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

class YourLikesViewModel : ViewModel() {

    private val repository = SongRepository()

    private val _likedSongs = MutableLiveData<List<Song>>()
    val likedSongs: LiveData<List<Song>> = _likedSongs

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    fun loadLikedSongs() {
        viewModelScope.launch {
            try {
                _likedSongs.value = repository.getLikedSongs()
            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
            } catch (e: Exception) {
                _errorMessageResId.value = R.string.load_liked_songs_failed
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}
