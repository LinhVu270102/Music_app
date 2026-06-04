package com.example.music_app.ui.auth

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.music_app.R
import com.example.music_app.data.repository.AuthRepository
import com.example.music_app.utils.AppException
import com.google.firebase.auth.FirebaseUser

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _authSuccess = MutableLiveData<Boolean>()
    val authSuccess: LiveData<Boolean> = _authSuccess

    private val _currentUser = MutableLiveData<FirebaseUser?>()
    val currentUser: LiveData<FirebaseUser?> = _currentUser

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun login(email: String, password: String) {
        val cleanEmail = email.trim()

        if (cleanEmail.isBlank() || password.isBlank()) {
            _errorMessageResId.value = R.string.empty_email_or_password
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            _errorMessageResId.value = R.string.invalid_email
            return
        }

        _isLoading.value = true

        repository.login(cleanEmail, password)
            .addOnSuccessListener {
                _authSuccess.value = true
                _currentUser.value = repository.getCurrentUser()
                _isLoading.value = false
            }
            .addOnFailureListener { e ->
                _authSuccess.value = false
                _errorMessageResId.value = if (e is AppException) {
                    e.messageResId
                } else {
                    R.string.login_failed
                }
                _isLoading.value = false
            }
    }

    fun register(
        displayName: String,
        email: String,
        password: String
    ) {
        val cleanDisplayName = displayName.trim()
        val cleanEmail = email.trim()

        if (cleanDisplayName.isBlank()) {
            _errorMessageResId.value = R.string.empty_display_name
            return
        }

        if (cleanEmail.isBlank() || password.isBlank()) {
            _errorMessageResId.value = R.string.empty_email_or_password
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            _errorMessageResId.value = R.string.invalid_email
            return
        }

        if (password.length < 6) {
            _errorMessageResId.value = R.string.password_too_short
            return
        }

        _isLoading.value = true

        repository.register(
            displayName = cleanDisplayName,
            email = cleanEmail,
            password = password
        ).addOnSuccessListener {
            _authSuccess.value = true
            _currentUser.value = repository.getCurrentUser()
            _isLoading.value = false
        }.addOnFailureListener { e ->
            _authSuccess.value = false
            _errorMessageResId.value = if (e is AppException) {
                e.messageResId
            } else {
                R.string.register_failed
            }
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

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}