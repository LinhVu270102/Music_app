package com.example.music_app.ui.search

import com.example.music_app.data.model.Song

/** Prepared list data for one selected search category. */
data class SearchPresentation(
    val items: List<SearchResultItem> = emptyList(),
    val playableSongs: List<Song> = emptyList()
)
