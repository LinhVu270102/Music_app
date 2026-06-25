package com.example.music_app.ui.comment

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Comment
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.CommentRepository
import com.example.music_app.player.state.PlayerInteractionState
import com.example.music_app.player.state.SongCommentState
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

class CommentViewModel : ViewModel() {

    private val commentRepository = CommentRepository()

    private val _song = MutableLiveData<Song?>()
    val song: LiveData<Song?> = _song

    private val _comments = MutableLiveData<List<Comment>>(emptyList())
    val comments: LiveData<List<Comment>> = _comments

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    private val _successMessageResId = MutableLiveData<Int?>()
    val successMessageResId: LiveData<Int?> = _successMessageResId

    fun getCurrentUserId(): String = commentRepository.getCurrentUserId()

    fun loadSong(
        songId: String,
        fallbackSong: Song? = null
    ) {
        viewModelScope.launch {
            try {
                _song.value = commentRepository.getSong(songId, fallbackSong)
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.invalid_song
            }
        }
    }

    fun loadComments(songId: String) {
        viewModelScope.launch {
            try {
                val comments = commentRepository.getComments(songId)
                _comments.value = comments
                publishCommentCount(songId, comments)
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.load_comments_failed
            }
        }
    }

    fun addComment(
        songId: String,
        content: String,
        timelinePositionMs: Long = 0L
    ) {
        viewModelScope.launch {
            try {
                commentRepository.addComment(
                    songId = songId,
                    content = content,
                    timelinePositionMs = timelinePositionMs
                )

                _successMessageResId.value = R.string.comment_added_success
                loadComments(songId)
                loadSong(songId)
            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.add_comment_failed
            }
        }
    }

    fun reportComment(
        songId: String,
        comment: Comment,
        reason: String,
        description: String = ""
    ) {
        if (reason.isBlank()) {
            _errorMessageResId.value = R.string.report_reason_empty
            return
        }

        viewModelScope.launch {
            try {
                commentRepository.reportComment(
                    songId = songId,
                    comment = comment,
                    reason = reason,
                    description = description
                )

                _successMessageResId.value = R.string.report_comment_success
            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.report_comment_failed
            }
        }
    }

    fun hideComment(
        songId: String,
        comment: Comment
    ) {
        viewModelScope.launch {
            try {
                commentRepository.hideComment(songId, comment)

                _successMessageResId.value = R.string.comment_hidden_success
                loadComments(songId)
                loadSong(songId)
            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.comment_hidden_failed
            }
        }
    }

    fun toggleCommentLike(songId: String, comment: Comment) {
        viewModelScope.launch {
            try {
                commentRepository.toggleCommentLike(songId, comment)
                loadComments(songId)
            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.update_like_failed
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }

    fun clearSuccessMessage() {
        _successMessageResId.value = null
    }

    private fun publishCommentCount(songId: String, comments: List<Comment>) {
        PlayerInteractionState.publishSongComments(
            SongCommentState(
                songId = songId,
                commentsCount = comments.size.toLong()
            )
        )
    }
}
