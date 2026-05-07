package com.example.music_app.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.music_app.data.repository.AuthRepository
import com.google.firebase.auth.FirebaseUser

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _authSuccess = MutableLiveData<Boolean>()
    val authSuccess: LiveData<Boolean> = _authSuccess

    private val _currentUser = MutableLiveData<FirebaseUser?>()
    val currentUser: LiveData<FirebaseUser?> = _currentUser

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _errorMessage.value = "Vui lòng nhập email và mật khẩu"
            return
        }

        _isLoading.value = true

        repository.login(email, password)
            .addOnSuccessListener {
                _authSuccess.value = true
                _currentUser.value = repository.getCurrentUser()
                _isLoading.value = false
            }
            .addOnFailureListener { e ->
                _authSuccess.value = false
                _errorMessage.value = e.message ?: "Đăng nhập thất bại"
                _isLoading.value = false
            }
    }

    fun register(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _errorMessage.value = "Vui lòng nhập email và mật khẩu"
            return
        }

        if (password.length < 6) {
            _errorMessage.value = "Mật khẩu phải có ít nhất 6 ký tự"
            return
        }

        _isLoading.value = true

        repository.register(email, password)
            .addOnSuccessListener {
                _authSuccess.value = true
                _currentUser.value = repository.getCurrentUser()
                _isLoading.value = false
            }
            .addOnFailureListener { e ->
                _authSuccess.value = false
                _errorMessage.value = e.message ?: "Đăng ký thất bại"
                _isLoading.value = false
            }
    }

    fun logout() {
        repository.logout()
        _currentUser.value = null
        _authSuccess.value = false
    }

    fun getCurrentUser() {
        _currentUser.value = repository.getCurrentUser()
    }
}