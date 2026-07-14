package com.example.music_app.ui.search.state

import androidx.annotation.StringRes
import com.example.music_app.data.model.Song

sealed class AudioSearchUiState {
    data object Idle : AudioSearchUiState()
    data object Searching : AudioSearchUiState()
    data class Success(val songs: List<Song>) : AudioSearchUiState()
    data object NoMatch : AudioSearchUiState()
    data class Error(@param:StringRes val messageResId: Int) : AudioSearchUiState()
}
