package com.example.music_app.player

import com.example.music_app.data.model.Song
import com.example.music_app.data.model.SongStatus
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSongSelectorTest {

    @Test
    fun `sanitize fallback keeps only approved playable unique songs`() {
        val approved = song("approved")
        val duplicate = approved.copy(title = "Duplicate")
        val deleted = song("deleted", isDeleted = true)
        val pending = song("pending", status = SongStatus.PENDING)
        val emptyUrl = song("empty-url", url = "")

        val result = PlaybackSongSelector.sanitizeFallbackSongs(
            listOf(approved, duplicate, deleted, pending, emptyUrl)
        )

        assertEquals(listOf(approved), result)
    }

    @Test
    fun `random fallback honours exclusions before choosing alternatives`() {
        val current = song("current")
        val excluded = song("excluded")
        val eligible = song("eligible")

        val result = PlaybackSongSelector.selectRandomFallback(
            songs = listOf(current, excluded, eligible),
            currentSongId = current.id,
            excludedSongIds = setOf(excluded.id),
            random = Random(0)
        )

        assertEquals(eligible, result)
    }

    @Test
    fun `random tail never returns a song already queued`() {
        val queued = song("queued")
        val candidate = song("candidate")
        val deleted = song("deleted", isDeleted = true)

        val result = PlaybackSongSelector.selectRandomTail(
            songs = listOf(queued, candidate, deleted),
            queuedSongIds = setOf(queued.id),
            limit = 5,
            random = Random(0)
        )

        assertEquals(listOf(candidate), result)
        assertTrue(result.none { it.id == queued.id })
    }

    private fun song(
        id: String,
        url: String = "https://example.com/$id.mp3",
        status: String = SongStatus.APPROVED,
        isDeleted: Boolean = false
    ): Song {
        return Song(
            id = id,
            title = id,
            songUrl = url,
            status = status,
            isDeleted = isDeleted
        )
    }
}
