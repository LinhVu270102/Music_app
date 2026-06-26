package com.example.music_app.player.state

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/** Shared UI state between the full player and the mini player. */
data class SongLikeState(
    val songId: String,
    val liked: Boolean,
    val likesCount: Long? = null,
    val commentsCount: Long? = null,
    val changedByUser: Boolean = false
)

data class SongCommentState(
    val songId: String,
    val commentsCount: Long
)

data class ArtistFollowState(
    val userId: String,
    val followed: Boolean,
    val followerCount: Long? = null
)

object PlayerInteractionState {
    private val songStates = mutableMapOf<String, SongLikeState>()
    private val songCommentCounts = mutableMapOf<String, Long>()
    private val artistStates = mutableMapOf<String, ArtistFollowState>()

    private val _songLikeUpdates = MutableLiveData<SongLikeState>()
    val songLikeUpdates: LiveData<SongLikeState> = _songLikeUpdates

    private val _songCommentUpdates = MutableLiveData<SongCommentState>()
    val songCommentUpdates: LiveData<SongCommentState> = _songCommentUpdates

    private val _artistFollowUpdates = MutableLiveData<ArtistFollowState>()
    val artistFollowUpdates: LiveData<ArtistFollowState> = _artistFollowUpdates

    @Synchronized
    fun songState(songId: String): SongLikeState? = songStates[songId]

    @Synchronized
    fun commentCount(songId: String): Long? = songCommentCounts[songId]

    @Synchronized
    fun artistState(userId: String): ArtistFollowState? = artistStates[userId]

    @Synchronized
    fun publishSongLike(state: SongLikeState) {
        val currentCommentCount = songCommentCounts[state.songId]
        val resolvedState = state.copy(
            commentsCount = currentCommentCount ?: state.commentsCount
        )

        resolvedState.commentsCount?.let { count ->
            songCommentCounts[state.songId] = count
        }

        songStates[state.songId] = resolvedState
        _songLikeUpdates.postValue(resolvedState)
    }

    @Synchronized
    fun publishSongComments(state: SongCommentState) {
        if (state.songId.isBlank()) return

        songCommentCounts[state.songId] = state.commentsCount
        songStates[state.songId] = songStates[state.songId]?.copy(
            commentsCount = state.commentsCount
        ) ?: SongLikeState(
            songId = state.songId,
            liked = false,
            commentsCount = state.commentsCount
        )
        _songCommentUpdates.postValue(state)
    }

    @Synchronized
    fun publishArtistFollow(state: ArtistFollowState) {
        artistStates[state.userId] = state
        _artistFollowUpdates.postValue(state)
    }
}
