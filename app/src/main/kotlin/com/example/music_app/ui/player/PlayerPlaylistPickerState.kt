package com.example.music_app.ui.player

import com.example.music_app.data.model.Song

/** One-time UI state used to open the correct playlist picker for the current song. */
data class PlayerPlaylistPickerState(
    val song: Song,
    val options: List<PlayerPlaylistOption>
)

data class PlayerPlaylistOption(
    val id: String,
    val name: String,
    val songsCount: Long
)
