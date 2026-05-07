package com.example.music_app.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SearchViewModel : ViewModel() {

    private val suggestions = listOf(
        "Sơn Tùng M-TP",
        "Đen Vâu",
        "MONO",
        "HIEUTHUHAI",
        "Chill music",
        "Lofi",
        "Rap Việt",
        "US-UK"
    )

    private val _results = MutableLiveData<List<String>>(emptyList())
    val results: LiveData<List<String>> = _results

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
        _results.value = suggestions
    }

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
        _showCancel.value = newQuery.isNotEmpty()

        _results.value = if (newQuery.isEmpty()) {
            suggestions
        } else {
            suggestions.filter {
                it.contains(newQuery, ignoreCase = true)
            }
        }
    }

    fun clearQuery() {
        _query.value = ""
        _showCancel.value = false
        _results.value = suggestions
    }
}