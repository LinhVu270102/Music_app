package com.example.music_app.ui.search

import com.example.music_app.data.model.SearchResultBundle
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultComposerTest {

    private val composer = SearchResultComposer()

    @Test
    fun `matches Vietnamese accents and exposes matching tracks as playable`() {
        val cafeSong = Song(
            id = "cafe",
            title = "Cà Phê Không Đường",
            songUrl = "https://example.com/cafe.mp3"
        )
        val otherSong = Song(
            id = "rain",
            title = "Rainy day",
            songUrl = "https://example.com/rain.mp3"
        )

        val presentation = composer.compose(
            result = SearchResultBundle(tracks = listOf(cafeSong, otherSong)),
            keyword = "ca phe",
            tab = SearchTab.TRACKS
        )

        assertEquals(listOf(cafeSong), presentation.playableSongs)
        assertEquals(1, presentation.items.size)
        val item = presentation.items.single()
        assertTrue(item is SearchResultItem.Track)
        assertEquals(cafeSong, (item as SearchResultItem.Track).song)
    }

    @Test
    fun `ranks an exact title match above a partial title match`() {
        val exactSong = Song(id = "exact", title = "Orange", songUrl = "https://example.com/a.mp3")
        val partialSong = Song(id = "partial", title = "Orange Sunset", songUrl = "https://example.com/b.mp3")

        val presentation = composer.compose(
            result = SearchResultBundle(tracks = listOf(partialSong, exactSong)),
            keyword = "orange",
            tab = SearchTab.ALL
        )

        val firstItem = presentation.items.first()
        assertTrue(firstItem is SearchResultItem.Track)
        assertEquals(exactSong, (firstItem as SearchResultItem.Track).song)
    }

    @Test
    fun `all tab orders track playlist and user when relevance is equal`() {
        val track = Song(id = "track", title = "Orange", songUrl = "https://example.com/track.mp3")
        val playlist = Playlist(id = "playlist", name = "Orange", ownerId = "owner")
        val user = User(uid = "user", displayName = "Orange")

        val presentation = composer.compose(
            result = SearchResultBundle(
                tracks = listOf(track),
                playlists = listOf(playlist),
                profiles = listOf(user)
            ),
            keyword = "orange",
            tab = SearchTab.ALL
        )

        assertTrue(presentation.items[0] is SearchResultItem.Track)
        assertTrue(presentation.items[1] is SearchResultItem.PlaylistItem)
        assertTrue(presentation.items[2] is SearchResultItem.Profile)
    }

    @Test
    fun `all tab prioritizes a closer keyword match before result type`() {
        val partialTrack = Song(
            id = "track",
            title = "Orange Sunset",
            songUrl = "https://example.com/track.mp3"
        )
        val exactUser = User(uid = "user", displayName = "Orange")

        val presentation = composer.compose(
            result = SearchResultBundle(
                tracks = listOf(partialTrack),
                profiles = listOf(exactUser)
            ),
            keyword = "orange",
            tab = SearchTab.ALL
        )

        assertTrue(presentation.items.first() is SearchResultItem.Profile)
    }

    @Test
    fun `user tab contains only persisted user profiles`() {
        val legacyTrack = Song(
            id = "legacy_1",
            title = "Track",
            artist = "Orange",
            songUrl = "https://example.com/track.mp3"
        )

        val presentation = composer.compose(
            result = SearchResultBundle(tracks = listOf(legacyTrack)),
            keyword = "orange",
            tab = SearchTab.PROFILES
        )

        assertTrue(presentation.items.isEmpty())
    }
}
