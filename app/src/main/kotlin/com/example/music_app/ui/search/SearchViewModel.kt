package com.example.music_app.ui.search

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {

    private val repository = SongRepository()

    private var allSongs: List<Song> = emptyList()

    private val _results = MutableLiveData<List<Song>>(emptyList())
    val results: LiveData<List<Song>> = _results

    private val _query = MutableLiveData("")
    val query: LiveData<String> = _query

    private val _showReturn = MutableLiveData(false)
    val showReturn: LiveData<Boolean> = _showReturn

    private val _showCancel = MutableLiveData(false)
    val showCancel: LiveData<Boolean> = _showCancel

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun onFocusChanged(hasFocus: Boolean) {
        _showReturn.value = hasFocus
        _showCancel.value = hasFocus && !_query.value.isNullOrEmpty()
    }

    fun loadSuggestions() {
        viewModelScope.launch {
            try {
                allSongs = repository.getAllSongs()
                _results.value = allSongs.take(10)
            } catch (e: Exception) {
                _errorMessage.value = e.message
                Log.e("SearchViewModel", "Load suggestions error: ${e.message}", e)
            }
        }
    }

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
        _showCancel.value = newQuery.isNotEmpty()

        if (newQuery.isBlank()) {
            _results.value = allSongs.take(10)
            return
        }

        val filtered = allSongs.filter { song ->
            song.title.contains(newQuery, ignoreCase = true) ||
                    song.artist.contains(newQuery, ignoreCase = true)
        }

        _results.value = filtered
    }

    fun clearQuery() {
        _query.value = ""
        _showCancel.value = false
        _results.value = allSongs.take(10)
    }
}