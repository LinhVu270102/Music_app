package com.example.music_app.ui.comment

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Comment
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch

class CommentViewModel : ViewModel() {

    private val songRepository = SongRepository()

    private val _song = MutableLiveData<Song?>()
    val song: LiveData<Song?> = _song

    private val _comments = MutableLiveData<List<Comment>>(emptyList())
    val comments: LiveData<List<Comment>> = _comments

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    private val _successMessageResId = MutableLiveData<Int?>()
    val successMessageResId: LiveData<Int?> = _successMessageResId

    fun loadSong(songId: String) {
        viewModelScope.launch {
            try {
                _song.value = songRepository.getSong(songId)
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.invalid_song
            }
        }
    }

    fun loadComments(songId: String) {
        viewModelScope.launch {
            try {
                _comments.value = songRepository.getComments(songId)
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.load_comments_failed
            }
        }
    }

    fun addComment(songId: String, content: String) {
        viewModelScope.launch {
            try {
                songRepository.addComment(songId, content)
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
                val finalDescription =
                    if (description.isBlank()) {
                        comment.content
                    } else {
                        description
                    }

                songRepository.reportComment(
                    songId = songId,
                    commentId = comment.id,
                    reason = reason,
                    description = finalDescription
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
                songRepository.softDeleteComment(
                    songId = songId,
                    commentId = comment.id
                )
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

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }

    fun clearSuccessMessage() {
        _successMessageResId.value = null
    }
}