package com.example.music_app.data.repository

import android.util.Log
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.SearchResultBundle
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.User
import com.example.music_app.data.model.enums.SongStatus
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

class SearchRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun search(keyword: String): SearchResultBundle {
        val query = keyword.trim()

        if (query.isBlank()) {
            return SearchResultBundle()
        }

        return coroutineScope {
            val tracksDeferred = async { searchTracks(query) }
            val profilesDeferred = async { searchProfiles(query) }
            val playlistsDeferred = async { searchPlaylists(query) }
            val tracks = tracksDeferred.await()
            val persistedProfiles = profilesDeferred.await()

            SearchResultBundle(
                tracks = tracks,
                profiles = mergePersistedAndArtistProfiles(
                    tracks = tracks,
                    persistedProfiles = persistedProfiles
                ),
                playlists = playlistsDeferred.await()
            )
        }
    }

    private suspend fun searchTracks(keyword: String): List<Song> {
        return fetchApprovedTracks()
            .filter { song -> song.matchesKeyword(keyword) }
            .distinctBy(Song::id)
            .rankTracks(keyword)
    }

    private suspend fun fetchApprovedTracks(): List<Song> {
        return APPROVED_SEARCH_STATUSES.flatMap { status ->
            fetchTracksByStatus(status)
        }
    }

    private suspend fun fetchTracksByStatus(status: String): List<Song> = safeSearch(
        label = "fetchTracksByStatus status=$status"
    ) {
        firestore.collection("songs")
            .whereEqualTo("status", status)
            .whereEqualTo("isDeleted", false)
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                doc.toObject(Song::class.java)?.copy(id = doc.id)
            }
    }

    private suspend fun searchProfiles(keyword: String): List<User> {
        return fetchProfiles()
            .filter { user -> user.matchesKeyword(keyword) }
            .take(RESULT_LIMIT)
    }

    private suspend fun fetchProfiles(): List<User> = safeSearch(
        label = "fetchProfiles"
    ) {
        firestore.collection("users")
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                doc.toObject(User::class.java)?.copy(uid = doc.id)
            }
    }

    private suspend fun searchPlaylists(keyword: String): List<Playlist> {
        val query = keyword.trim()

        if (query.isBlank()) return emptyList()

        val firestorePlaylists = fetchPublicPlaylists()
            .filter { playlist -> playlist.matchesKeyword(query) }

        val result = firestorePlaylists
            .distinctBy(Playlist::id)
            .rankPlaylists(query)
            .take(RESULT_LIMIT)

        Log.d(
            TAG,
            "searchPlaylists query=$query firestore=${firestorePlaylists.size}, total=${result.size}"
        )

        return result
    }

    private suspend fun fetchPublicPlaylists(): List<Playlist> = safeSearch(
        label = "fetchPublicPlaylists"
    ) {
        // PlaylistRemoteDataSource keeps a public root mirror specifically for search.
        // Querying that mirror avoids a collection-group index dependency and includes
        // catalog playlists and public user playlists.
        firestore.collection("playlists")
            .whereEqualTo("isPublic", true)
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                doc.toObject(Playlist::class.java)?.copy(id = doc.id)
            }
    }

    private suspend fun <T> safeSearch(
        label: String,
        block: suspend () -> List<T>
    ): List<T> {
        return try {
            block()
        } catch (error: Exception) {
            Log.e(TAG, "$label failed: ${error.message}", error)
            emptyList()
        }
    }

    private fun List<Song>.rankTracks(keyword: String): List<Song> {
        return sortedWith(
            compareByDescending<Song> { song ->
                song.title.contains(keyword, ignoreCase = true)
            }.thenByDescending { song ->
                song.likes + song.plays
            }
        )
    }

    private fun List<Playlist>.rankPlaylists(keyword: String): List<Playlist> {
        return sortedWith(
            compareByDescending<Playlist> { playlist ->
                playlist.name.contains(keyword, ignoreCase = true)
            }.thenByDescending { playlist ->
                playlist.songsCount
            }
        )
    }

    private fun Song.matchesKeyword(keyword: String): Boolean {
        return title.contains(keyword, ignoreCase = true) ||
                artist.contains(keyword, ignoreCase = true) ||
                genre.contains(keyword, ignoreCase = true) ||
                tags.any { tag ->
                    tag.contains(keyword, ignoreCase = true)
                }
    }

    private fun User.matchesKeyword(keyword: String): Boolean {
        return displayName.contains(keyword, ignoreCase = true) ||
                username.contains(keyword, ignoreCase = true) ||
                email.contains(keyword, ignoreCase = true) ||
                fullName.contains(keyword, ignoreCase = true)
    }

    private fun createArtistProfiles(
        tracks: List<Song>,
        persistedProfiles: List<User>
    ): List<User> {
        val persistedArtistNames = persistedProfiles
            .flatMap { user ->
                listOf(user.displayName, user.fullName, user.username)
            }
            .map(::normalize)
            .filter(String::isNotBlank)
            .toSet()

        return tracks
            .filter { track -> normalize(track.artist).isNotBlank() }
            .groupBy { track -> normalize(track.artist) }
            .filterKeys { artistName -> artistName !in persistedArtistNames }
            .map { (normalizedArtist, artistTracks) ->
                val firstTrack = artistTracks.first()
                User(
                    uid = "$SYNTHETIC_ARTIST_PREFIX$normalizedArtist",
                    displayName = firstTrack.artist.trim(),
                    username = firstTrack.artist.trim(),
                    avatarUrl = firstTrack.coverUrl,
                    bio = "",
                    fullName = firstTrack.artist.trim(),
                    uploadedSongsCount = artistTracks.size.toLong()
                )
            }
    }

    private fun mergePersistedAndArtistProfiles(
        tracks: List<Song>,
        persistedProfiles: List<User>
    ): List<User> {
        val artistProfiles = createArtistProfiles(
            tracks = tracks,
            persistedProfiles = persistedProfiles
        )

        return (persistedProfiles + artistProfiles).distinctBy(User::uid)
    }

    private fun Playlist.matchesKeyword(keyword: String): Boolean {
        return name.contains(keyword, ignoreCase = true) ||
                description.contains(keyword, ignoreCase = true)
    }

    private fun normalize(value: String): String {
        return java.text.Normalizer
            .normalize(value, java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .trim()
            .lowercase()
    }

    companion object {
        const val SYNTHETIC_ARTIST_PREFIX = "artist:"
        private const val TAG = "SearchRepository"
        private const val RESULT_LIMIT = 20
        private val APPROVED_SEARCH_STATUSES = listOf(
            SongStatus.APPROVED.value,
            "APPROVED"
        )
    }
}
