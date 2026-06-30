package com.example.music_app.data.repository

import com.example.music_app.R
import com.example.music_app.data.model.Report
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.enums.ReportStatus
import com.example.music_app.data.model.enums.ReportTargetType
import com.example.music_app.data.model.User
import com.example.music_app.data.model.enums.SongStatus
import com.example.music_app.data.model.enums.UserRole
import com.example.music_app.data.remote.ReportRemoteDataSource
import com.example.music_app.data.remote.SongRemoteDataSource
import com.example.music_app.data.remote.UserRemoteDataSource
import com.example.music_app.utils.AppException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** Owns songs and song reports. Playlist, comment, and social data live in dedicated repositories. */
class SongRepository {

    private val db = FirebaseFirestore.getInstance()
    private val songRemoteDataSource = SongRemoteDataSource(db)
    private val userRemoteDataSource = UserRemoteDataSource(db)
    private val reportRemoteDataSource = ReportRemoteDataSource(db)
    private val auth = FirebaseAuth.getInstance()

    fun getCurrentUserId(): String = auth.currentUser?.uid.orEmpty()

    // =========================
    // SONG BASIC
    // =========================

    suspend fun getSong(songId: String): Song? {
        return songRemoteDataSource.getSongById(songId)
    }

    suspend fun getAllSongs(): List<Song> {
        // Firestore rules only allow a public query that explicitly constrains
        // the catalog to approved, non-deleted songs.
        val approvedSongs = songRemoteDataSource.getApprovedSongs()
        // Do not let a legacy query denied by an older deployed rule hide the
        // already-readable, normalized catalog.
        val legacySongs = runCatching {
            songRemoteDataSource.getLegacyApprovedSongs()
        }.getOrDefault(emptyList())

        return (approvedSongs + legacySongs)
            .distinctBy(Song::id)
            .filter { song -> song.isApprovedVisible() }
    }

    suspend fun upsertSong(song: Song) {
        songRemoteDataSource.upsertSong(song)
    }

    suspend fun getRecentlyPlayedSongs(): List<Song> {
        val userId = auth.currentUser?.uid ?: return emptyList()

        return songRemoteDataSource.getRecentlyPlayedSongs(userId)
            .filter { song -> song.isApprovedVisible() }
    }

    // =========================
    // USER PROFILE / UPLOAD
    // =========================

    suspend fun getCurrentUserProfile(): User? {
        val userId = auth.currentUser?.uid ?: return null
        return userRemoteDataSource.getById(userId)
    }


    suspend fun getMyUploadedSongs(): List<Song> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return songRemoteDataSource.getSongsByUploaderId(userId)
    }


    suspend fun softDeleteMySong(songId: String) {
        val userId = auth.currentUser?.uid
            ?: throw AppException(R.string.not_logged_in)

        val song = songRemoteDataSource.getSongById(songId)
            ?: throw AppException(R.string.invalid_song)

        val currentUser = userRemoteDataSource.getById(userId)
            ?: throw AppException(R.string.user_not_found)

        val isOwner = song.uploaderId == userId
        val isAdmin = currentUser.roleType == UserRole.ADMIN

        if (!isOwner && !isAdmin) {
            throw AppException(R.string.no_permission)
        }

        songRemoteDataSource.softDeleteSong(
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

        val song = songRemoteDataSource.getSongById(songId)
            ?: throw AppException(R.string.invalid_song)

        val currentUser = userRemoteDataSource.getById(userId)
            ?: throw AppException(R.string.user_not_found)

        val isOwner = song.uploaderId == userId
        val isAdmin = currentUser.roleType == UserRole.ADMIN

        if (!isOwner && !isAdmin) {
            throw AppException(R.string.no_permission)
        }

        songRemoteDataSource.updateSongCommentPermission(
            songId = songId,
            allowComments = allowComments
        )
    }


    suspend fun getSongsByUserId(userId: String): List<Song> {
        return songRemoteDataSource.getApprovedSongsByUploaderId(userId)
    }

    suspend fun getSongsByArtistName(artistName: String): List<Song> {
        val normalizedArtistName = normalizeArtistName(artistName)
        if (normalizedArtistName.isBlank()) return emptyList()

        return getAllSongs().filter { song ->
            normalizeArtistName(song.artist) == normalizedArtistName
        }
    }

    suspend fun getUserById(userId: String): User? {
        return userRemoteDataSource.getById(userId)
    }

    suspend fun resubmitMyRejectedSong(songId: String) {
        val userId = auth.currentUser?.uid ?: throw AppException(R.string.not_logged_in)
        val song = songRemoteDataSource.getSongById(songId) ?: throw AppException(R.string.invalid_song)

        if (song.uploaderId != userId) {
            throw AppException(R.string.no_permission)
        }

        if (song.statusType != SongStatus.REJECTED) {
            throw AppException(R.string.only_rejected_song_can_resubmit)
        }

        songRemoteDataSource.resubmitSongForReview(songId)
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

        val user = userRemoteDataSource.getById(userId)
            ?: throw AppException(R.string.user_not_found)

        val song = songRemoteDataSource.getSongById(songId)
            ?: throw AppException(R.string.invalid_song)

        if (song.isDeleted) {
            throw AppException(R.string.song_deleted)
        }

        val report = Report(
            targetId = songId,
            targetType = ReportTargetType.SONG.value,
            reporterId = userId,
            reporterName = user.displayName.ifBlank { user.email },
            reason = reason,
            description = description,
            status = ReportStatus.PENDING.value
        )

        return reportRemoteDataSource.create(report)
    }

    private fun normalizeArtistName(value: String): String {
        return java.text.Normalizer
            .normalize(value, java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .trim()
            .lowercase()
    }

    private fun Song.isApprovedVisible(): Boolean {
        return statusType == SongStatus.APPROVED && !isDeleted
    }

}
