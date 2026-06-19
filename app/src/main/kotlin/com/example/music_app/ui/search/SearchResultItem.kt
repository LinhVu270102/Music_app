package com.example.music_app.ui.search

import androidx.annotation.StringRes
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.User

sealed class SearchResultItem {

    data class Header(
        @StringRes val titleResId: Int,
        val count: Int
    ) : SearchResultItem()

    data class Track(
        val song: Song
    ) : SearchResultItem()

    data class Profile(
        val user: User
    ) : SearchResultItem()

    data class PlaylistItem(
        val playlist: Playlist
    ) : SearchResultItem()
}