package com.example.music_app.ui.search

import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.SearchResultBundle
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.User
import com.example.music_app.data.model.enums.SearchTab

/**
 * Pure search presentation rules: keyword matching, result grouping, and ranking.
 * Keeping this outside the Fragment makes the search screen responsible only for UI state.
 */
class SearchResultComposer {

    fun compose(
        result: SearchResultBundle,
        keyword: String,
        tab: SearchTab
    ): SearchPresentation {
        val normalizedKeyword = normalize(keyword)
        if (normalizedKeyword.isBlank()) return SearchPresentation()

        return when (tab) {
            SearchTab.ALL -> composeAll(result, normalizedKeyword)
            SearchTab.TRACKS -> composeTracks(result.tracks, normalizedKeyword)
            SearchTab.PROFILES -> composeProfiles(result.profiles, normalizedKeyword)
            SearchTab.PLAYLISTS -> composePlaylists(result.playlists, normalizedKeyword)
        }
    }

    private fun composeAll(
        result: SearchResultBundle,
        normalizedKeyword: String
    ): SearchPresentation {
        val songs = result.tracks.filter { song -> matches(song, normalizedKeyword) }
        val items = songs.map(SearchResultItem::Track) +
            result.playlists
                .filter { playlist -> matches(playlist, normalizedKeyword) }
                .map(SearchResultItem::PlaylistItem) +
            result.profiles
                .filter { user -> matches(user, normalizedKeyword) }
                .map(SearchResultItem::Profile)

        return SearchPresentation(
            items = items.ranked(normalizedKeyword),
            playableSongs = songs
        )
    }

    private fun composeTracks(
        tracks: List<Song>,
        normalizedKeyword: String
    ): SearchPresentation {
        val songs = tracks.filter { song -> matches(song, normalizedKeyword) }

        return SearchPresentation(
            items = songs
                .map(SearchResultItem::Track)
                .ranked(normalizedKeyword),
            playableSongs = songs
        )
    }

    private fun composeProfiles(
        profiles: List<User>,
        normalizedKeyword: String
    ): SearchPresentation {
        val items = profiles
            .filter { user -> matches(user, normalizedKeyword) }
            .map(SearchResultItem::Profile)
            .ranked(normalizedKeyword)

        return SearchPresentation(items = items)
    }

    private fun composePlaylists(
        playlists: List<Playlist>,
        normalizedKeyword: String
    ): SearchPresentation {
        val items = playlists
            .filter { playlist -> matches(playlist, normalizedKeyword) }
            .map(SearchResultItem::PlaylistItem)
            .ranked(normalizedKeyword)

        return SearchPresentation(items = items)
    }

    private fun matches(song: Song, keyword: String): Boolean {
        return normalize(song.title).contains(keyword) ||
            normalize(song.artist).contains(keyword) ||
            normalize(song.genre).contains(keyword)
    }

    private fun matches(user: User, keyword: String): Boolean {
        return normalize(user.displayName).contains(keyword) ||
            normalize(user.username).contains(keyword) ||
            normalize(user.email).contains(keyword) ||
            normalize(user.fullName).contains(keyword)
    }

    private fun matches(playlist: Playlist, keyword: String): Boolean {
        return normalize(playlist.name).contains(keyword) ||
            normalize(playlist.description).contains(keyword)
    }

    private fun score(item: SearchResultItem, keyword: String): Int {
        return when (item) {
            is SearchResultItem.Track -> bestScore(
                normalize(item.song.title),
                normalize(item.song.artist),
                keyword
            )

            is SearchResultItem.Profile -> bestScore(
                normalize(item.user.displayName),
                normalize(item.user.username),
                normalize(item.user.fullName),
                normalize(item.user.email),
                keyword
            )

            is SearchResultItem.PlaylistItem -> bestScore(
                normalize(item.playlist.name),
                normalize(item.playlist.description),
                keyword
            )
            is SearchResultItem.Header, is SearchResultItem.RecentQuery -> 0
        }
    }

    private fun List<SearchResultItem>.ranked(keyword: String): List<SearchResultItem> {
        return sortedWith(
            compareByDescending<SearchResultItem> { item -> score(item, keyword) }
                .thenBy(::typePriority)
                .thenBy(::displayLabel)
        )
    }

    private fun typePriority(item: SearchResultItem): Int {
        return when (item) {
            is SearchResultItem.Track -> 0
            is SearchResultItem.PlaylistItem -> 1
            is SearchResultItem.Profile -> 2
            is SearchResultItem.Header, is SearchResultItem.RecentQuery -> 3
        }
    }

    private fun displayLabel(item: SearchResultItem): String {
        return when (item) {
            is SearchResultItem.Track -> normalize(item.song.title)
            is SearchResultItem.PlaylistItem -> normalize(item.playlist.name)
            is SearchResultItem.Profile -> normalize(item.user.displayName)
            is SearchResultItem.Header, is SearchResultItem.RecentQuery -> ""
        }
    }

    private fun bestScore(vararg valuesAndKeyword: String): Int {
        val keyword = valuesAndKeyword.last()
        val values = valuesAndKeyword.dropLast(1)

        return when {
            values.any { value -> value == keyword } -> 100
            values.any { value -> value.startsWith(keyword) } -> 90
            values.any { value -> value.contains(keyword) } -> 80
            else -> 0
        }
    }

    private fun normalize(value: String): String {
        return java.text.Normalizer
            .normalize(value, java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .trim()
            .lowercase()
    }

}
