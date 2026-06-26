package com.example.music_app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSourceTest {

    @Test
    fun `new playlist is public by default`() {
        assertTrue(Playlist().isPublic)
    }

    @Test
    fun `playlist keeps its owner and song count`() {
        val playlist = Playlist(
            id = "playlist_42",
            ownerId = "user_42",
            songsCount = 12L
        )

        assertEquals("user_42", playlist.ownerId)
        assertEquals(12L, playlist.songsCount)
    }
}
