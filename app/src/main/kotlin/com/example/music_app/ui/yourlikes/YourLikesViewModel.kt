package com.example.music_app.ui.yourlikes

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SocialRepository
import com.example.music_app.player.state.PlayerInteractionState
import com.example.music_app.player.state.SongLikeState
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

class YourLikesViewModel(
    private val repository: SocialRepository = SocialRepository()
) : ViewModel() {

    private val _likedSongs = MutableLiveData<List<Song>>()
    val likedSongs: LiveData<List<Song>> = _likedSongs

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    private val likeObserver = androidx.lifecycle.Observer<SongLikeState> {
        loadLikedSongs()
    }

    init {
        PlayerInteractionState.songLikeUpdates.observeForever(likeObserver)
    }

    fun loadLikedSongs() {
        viewModelScope.launch {
            try {
                publishLikedSongs(repository.getLikedSongs())
            } catch (e: AppException) {
                publishError(e.messageResId)
            } catch (e: Exception) {
                publishError(R.string.load_liked_songs_failed)
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }

    override fun onCleared() {
        PlayerInteractionState.songLikeUpdates.removeObserver(likeObserver)
        super.onCleared()
    }

    private fun publishLikedSongs(songs: List<Song>) {
        _likedSongs.value = songs
    }

    private fun publishError(messageResId: Int) {
        _errorMessageResId.value = messageResId
    }
}
