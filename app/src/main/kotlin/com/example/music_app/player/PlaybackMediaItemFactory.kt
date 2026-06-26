package com.example.music_app.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.example.music_app.data.model.Song

/** Maps an app [Song] into the Media3 item understood by ExoPlayer. */
object PlaybackMediaItemFactory {

    fun create(song: Song): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .build()

        val builder = MediaItem.Builder()
            .setUri(song.songUrl)
            .setMediaId(song.id)
            .setMediaMetadata(metadata)

        if (song.usesHlsStream()) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }

        return builder.build()
    }

    private fun Song.usesHlsStream(): Boolean {
        return tags.any { tag -> tag.equals("hls", ignoreCase = true) } ||
                songUrl.contains(".m3u8", ignoreCase = true) ||
                songUrl.contains("/hls", ignoreCase = true)
    }
}
