package com.example.music_app.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {

    private val repository = SongRepository()

    private val _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> = _songs

    private val _searchResults = MutableLiveData<List<Song>>()
    val searchResults: LiveData<List<Song>> = _searchResults

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var allSongs: List<Song> = emptyList()

    fun loadSongs() {
        viewModelScope.launch {
            try {
                allSongs = repository.getAllSongs()
                _songs.value = allSongs
                _searchResults.value = allSongs.take(10)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Không tải được dữ liệu tìm kiếm"
            }
        }
    }

    fun search(keyword: String) {
        val query = keyword.trim()

        if (query.isBlank()) {
            _searchResults.value = allSongs.take(10)
            return
        }

        val results = allSongs.filter { song ->
            song.title.contains(query, ignoreCase = true) ||
                    song.artist.contains(query, ignoreCase = true)
        }

        _searchResults.value = results
    }
}