package com.example.music_app.ui.comment

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.data.model.Comment
import com.example.music_app.data.repository.SongRepository
import kotlinx.coroutines.launch

class CommentViewModel : ViewModel() {

    private val repository = SongRepository()

    private val _comments = MutableLiveData<List<Comment>>()
    val comments: LiveData<List<Comment>> = _comments

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadComments(songId: String) {
        viewModelScope.launch {
            try {
                _comments.value = repository.getComments(songId)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Không tải được bình luận"
            }
        }
    }

    fun addComment(songId: String, content: String) {
        viewModelScope.launch {
            try {
                repository.addComment(songId, content)
                loadComments(songId)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Không gửi được bình luận"
            }
        }
    }
}