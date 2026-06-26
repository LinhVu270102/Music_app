package com.example.music_app.player

/**
 * Describes where the active queue came from. Keeping this separate from a
 * [Song] lets the full player show the playlist name without guessing from
 * the queue contents.
 */
data class PlaybackContext(
    val playlistId: String = "",
    val playlistName: String = "",
    val playlistOwnerId: String = "",
    val playlistCoverUrl: String = ""
) {
    val isPlaylist: Boolean
        get() = playlistId.isNotBlank() && playlistName.isNotBlank()
}
