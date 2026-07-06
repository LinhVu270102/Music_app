package com.example.music_app.data.repository

import com.example.music_app.R
import com.example.music_app.data.model.Report
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.enums.ReportStatus
import com.example.music_app.data.model.enums.ReportTargetType
import com.example.music_app.data.model.User
import com.example.music_app.data.model.enums.SongStatus
import com.example.music_app.data.firebase.firestore.ReportFirestoreDataSource
import com.example.music_app.data.firebase.firestore.SongFirestoreDataSource
import com.example.music_app.data.firebase.firestore.UserFirestoreDataSource
import com.example.music_app.utils.AppException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** Owns songs and song reports. Playlist, comment, and social data live in dedicated repositories. */
class SongRepository {

    private val db = FirebaseFirestore.getInstance()
    private val songFirestoreDataSource = SongFirestoreDataSource(db)
    private val userFirestoreDataSource = UserFirestoreDataSource(db)
    private val reportFirestoreDataSource = ReportFirestoreDataSource(db)
    private val auth = FirebaseAuth.getInstance()

    fun getCurrentUserId(): String = auth.currentUser?.uid.orEmpty()

    // =========================
    // SONG BASIC
    // =========================

    suspend fun getSong(songId: String): Song? {
        return songFirestoreDataSource.getSongById(songId)
    }

    suspend fun getAllSongs(): List<Song> {
        // Firestore rules only allow a public query that explicitly constrains
        // the catalog to approved, non-deleted songs.
        val approvedSongs = songFirestoreDataSource.getApprovedSongs()
        // Do not let a legacy query denied by an older deployed rule hide the
        // already-readable, normalized catalog.
        val legacySongs = runCatching {
            songFirestoreDataSource.getLegacyApprovedSongs()
        }.getOrDefault(emptyList())

        return (approvedSongs + legacySongs)
            .distinctBy(Song::id)
            .filter { song -> song.isApprovedVisible() }
    }

    suspend fun upsertSong(song: Song) {
        songFirestoreDataSource.upsertSong(song)
    }

    suspend fun getRecentlyPlayedSongs(): List<Song> {
        val userId = auth.currentUser?.uid ?: return emptyList()

        return songFirestoreDataSource.getRecentlyPlayedSongs(userId)
            .filter { song -> song.isApprovedVisible() }
    }

    // =========================
    // USER PROFILE / UPLOAD
    // =========================

    suspend fun getCurrentUserProfile(): User? {
        val userId = auth.currentUser?.uid ?: return null
        return userFirestoreDataSource.getById(userId)
    }


    suspend fun getMyUploadedSongs(): List<Song> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return songFirestoreDataSource.getSongsByUploaderId(userId)
    }


    suspend fun softDeleteMySong(songId: String) {
        val userId = auth.currentUser?.uid
            ?: throw AppException(R.string.not_logged_in)

        val song = songFirestoreDataSource.getSongById(songId)
            ?: throw AppException(R.string.invalid_song)

        val currentUser = userFirestoreDataSource.getById(userId)
            ?: throw AppException(R.string.user_not_found)

        val isOwner = song.uploaderId == userId
        val canModerate = currentUser.roleType.canModerateContent

        if (!isOwner && !canModerate) {
            throw AppException(R.string.no_permission)
        }

        songFirestoreDataSource.softDeleteSong(
            songId = songId,
            deletedBy = userId
        )
    }

    suspend fun updateMySongCommentPermission(
        songId: String,
        allowComments: Boolean
    ) {
        val userId = auth.currentUser?.uid
            ?: throw AppException(R.string.not_logged_in)

        val song = songFirestoreDataSource.getSongById(songId)
            ?: throw AppException(R.string.invalid_song)

        val currentUser = userFirestoreDataSource.getById(userId)
            ?: throw AppException(R.string.user_not_found)

        val isOwner = song.uploaderId == userId
        val canModerate = currentUser.roleType.canModerateContent

        if (!isOwner && !canModerate) {
            throw AppException(R.string.no_permission)
        }

        songFirestoreDataSource.updateSongCommentPermission(
            songId = songId,
            allowComments = allowComments
        )
    }


    suspend fun getSongsByUserId(userId: String): List<Song> {
        val approvedSongs = songFirestoreDataSource.getApprovedSongsByUploaderId(userId)
        val legacySongs = runCatching {
            songFirestoreDataSource.getLegacyApprovedSongsByUploaderId(userId)
        }.getOrDefault(emptyList())

        return (approvedSongs + legacySongs)
            .distinctBy(Song::id)
            .filter { song -> song.isApprovedVisible() }
    }

    suspend fun getSongsByArtistName(artistName: String): List<Song> {
        val normalizedArtistName = normalizeArtistName(artistName)
        if (normalizedArtistName.isBlank()) return emptyList()

        return getAllSongs().filter { song ->
            normalizeArtistName(song.artist) == normalizedArtistName
        }
    }

    suspend fun getUserById(userId: String): User? {
        return userFirestoreDataSource.getById(userId)
    }

    suspend fun resubmitMyRejectedSong(songId: String) {
        val userId = auth.currentUser?.uid ?: throw AppException(R.string.not_logged_in)
        val song = songFirestoreDataSource.getSongById(songId) ?: throw AppException(R.string.invalid_song)

        if (song.uploaderId != userId) {
            throw AppException(R.string.no_permission)
        }

        if (song.statusType != SongStatus.REJECTED) {
            throw AppException(R.string.only_rejected_song_can_resubmit)
        }

        songFirestoreDataSource.resubmitSongForReview(songId)
    }

    // =========================
    // REPORT
    // =========================


    suspend fun reportSong(
        songId: String,
        reason: String,
        description: String = ""
    ): Report {
        val userId = auth.currentUser?.uid
            ?: throw AppException(R.string.not_logged_in)

        val user = userFirestoreDataSource.getById(userId)
        val reporterName = user?.displayName
            ?.takeIf(String::isNotBlank)
            ?: user?.email?.takeIf(String::isNotBlank)
            ?: auth.currentUser?.displayName?.takeIf(String::isNotBlank)
            ?: auth.currentUser?.email?.takeIf(String::isNotBlank)
            ?: DEFAULT_REPORTER_NAME

        val song = songFirestoreDataSource.getSongById(songId)
            ?: throw AppException(R.string.invalid_song)

        if (song.isDeleted) {
            throw AppException(R.string.song_deleted)
        }

        val report = Report(
            targetId = songId,
            targetType = ReportTargetType.SONG.value,
            targetOwnerId = song.uploaderId,
            songId = song.id,
            songOwnerId = song.uploaderId,
            targetTitle = song.title.ifBlank { song.id },
            targetSubtitle = song.artist.ifBlank { song.uploaderId },
            targetPreview = song.genre,
            reporterId = userId,
            reporterName = reporterName,
            reason = reason,
            description = description,
            status = ReportStatus.PENDING.value
        )

        return reportFirestoreDataSource.create(report)
    }

    private fun normalizeArtistName(value: String): String {
        return java.text.Normalizer
            .normalize(value, java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .trim()
            .lowercase()
    }

    private fun Song.isApprovedVisible(): Boolean {
        return statusType == SongStatus.APPROVED &&
            !isDeleted &&
            songUrl.isNotBlank()
    }

    private companion object {
        private const val DEFAULT_REPORTER_NAME = "Orange Music user"
    }

}
