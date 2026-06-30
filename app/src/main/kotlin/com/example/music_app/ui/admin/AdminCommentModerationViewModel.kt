package com.example.music_app.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Comment
import com.example.music_app.data.repository.AdminRepository
import kotlinx.coroutines.launch

class AdminCommentModerationViewModel(
    private val adminRepository: AdminRepository = AdminRepository()
) : ViewModel() {

    private val _comments = MutableLiveData<List<Comment>>(emptyList())
    val comments: LiveData<List<Comment>> = _comments

    private val _messageResId = MutableLiveData<Int?>()
    val messageResId: LiveData<Int?> = _messageResId

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadReportedComments() {
        viewModelScope.launch {
            try {
                setLoading(true)
                publishComments(adminRepository.getReportedComments())
            } catch (_: Exception) {
                publishMessage(R.string.load_reported_comments_failed)
            } finally {
                setLoading(false)
            }
        }
    }

    fun hideComment(comment: Comment) {
        viewModelScope.launch {
            try {
                adminRepository.hideComment(comment)
                publishMessage(R.string.comment_hidden_success)
                loadReportedComments()
            } catch (_: Exception) {
                publishMessage(R.string.comment_hidden_failed)
            }
        }
    }

    fun clearMessage() {
        _messageResId.value = null
    }

    private fun publishComments(comments: List<Comment>) {
        _comments.value = comments
    }

    private fun publishMessage(messageResId: Int) {
        _messageResId.value = messageResId
    }

    private fun setLoading(isLoading: Boolean) {
        _isLoading.value = isLoading
    }
}
