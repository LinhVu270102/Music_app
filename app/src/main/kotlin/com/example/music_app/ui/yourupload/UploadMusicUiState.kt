package com.example.music_app.ui.yourupload

sealed interface UploadMusicUiState {
    data object Idle : UploadMusicUiState
    data object Loading : UploadMusicUiState
    data object Success : UploadMusicUiState
    data class Error(
        val messageResId: Int? = null,
        val message: String? = null
    ) : UploadMusicUiState
}
