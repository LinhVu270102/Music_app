package com.example.music_app.player

import com.example.music_app.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackQueueTest {

    @Test
    fun `replace removes invalid and duplicated songs`() {
        val queue = PlaybackQueue()
        val valid = song("valid")

        val result = queue.replace(
            listOf(
                valid,
                valid.copy(title = "Duplicate"),
                song(id = "", url = "https://example.com/empty-id.mp3"),
                song(id = "empty-url", url = "")
            )
        )

        assertEquals(listOf(valid), result)
        assertEquals(-1, queue.currentIndex)
    }

    @Test
    fun `selection and append preserve a unique ordered queue`() {
        val queue = PlaybackQueue()
        val first = song("first")
        val second = song("second")
        val third = song("third")

        queue.replace(listOf(first, second))

        assertEquals(second, queue.select(1))
        assertEquals(1, queue.currentIndex)
        assertNull(queue.select(8))
        assertEquals(1, queue.currentIndex)

        assertEquals(listOf(third), queue.append(listOf(first, third)))
        assertEquals(listOf(first, second, third), queue.snapshot())
    }

    private fun song(id: String, url: String = "https://example.com/$id.mp3"): Song {
        return Song(id = id, title = id, songUrl = url)
    }
}
