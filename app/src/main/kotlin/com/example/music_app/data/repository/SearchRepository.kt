package com.example.music_app.data.repository

import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.SearchResultBundle
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.SongStatus
import com.example.music_app.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

class SearchRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val soundCloudRepository = SoundCloudRepository()

    suspend fun search(keyword: String): SearchResultBundle {
        val query = keyword.trim()

        if (query.isBlank()) {
            return SearchResultBundle()
        }

        return coroutineScope {
            val tracksDeferred = async { searchTracks(query) }
            val profilesDeferred = async { searchProfiles(query) }
            val playlistsDeferred = async { searchPlaylists(query) }

            SearchResultBundle(
                tracks = tracksDeferred.await(),
                profiles = profilesDeferred.await(),
                playlists = playlistsDeferred.await()
            )
        }
    }

    private suspend fun searchTracks(keyword: String): List<Song> {
        val firebaseTracks = try {
            firestore.collection("songs")
                .whereEqualTo("status", SongStatus.APPROVED)
                .whereEqualTo("isDeleted", false)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    doc.toObject(Song::class.java)?.copy(id = doc.id)
                }
                .filter { song ->
                    song.matchesKeyword(keyword)
                }
        } catch (_: Exception) {
            emptyList()
        }

        val soundCloudTracks = try {
            soundCloudRepository.searchTracks(
                query = keyword,
                limit = 20
            )
        } catch (_: Exception) {
            emptyList()
        }

        return (firebaseTracks + soundCloudTracks)
            .distinctBy { song -> song.id }
            .sortedWith(
                compareByDescending<Song> { song ->
                    song.title.contains(keyword, ignoreCase = true)
                }.thenByDescending { song ->
                    song.likes + song.plays
                }
            )
    }

    private suspend fun searchProfiles(keyword: String): List<User> {
        return try {
            firestore.collection("users")
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    doc.toObject(User::class.java)?.copy(uid = doc.id)
                }
                .filter { user ->
                    user.matchesKeyword(keyword)
                }
                .take(20)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun searchPlaylists(keyword: String): List<Playlist> {
        return try {
            firestore.collectionGroup("playlists")
                .whereEqualTo("isPublic", true)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    doc.toObject(Playlist::class.java)?.copy(id = doc.id)
                }
                .filter { playlist ->
                    playlist.matchesKeyword(keyword)
                }
                .take(20)
        } catch (_: Exception) {
            emptyList()
        }
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

    private fun Playlist.matchesKeyword(keyword: String): Boolean {
        return name.contains(keyword, ignoreCase = true) ||
                description.contains(keyword, ignoreCase = true)
    }
}