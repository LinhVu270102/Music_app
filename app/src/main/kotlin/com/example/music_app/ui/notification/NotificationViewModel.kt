package com.example.music_app.ui.notification

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.AppNotification
import com.example.music_app.data.repository.NotificationRepository
import kotlinx.coroutines.launch

class NotificationViewModel : ViewModel() {
    private val repository = NotificationRepository()

    private val _notifications = MutableLiveData<List<AppNotification>>(emptyList())
    val notifications: LiveData<List<AppNotification>> = _notifications

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    fun loadNotifications() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                _notifications.value = repository.getNotifications()
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.load_notifications_failed
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun markRead(notification: AppNotification) {
        if (notification.isRead || notification.id.isBlank()) return

        viewModelScope.launch {
            try {
                repository.markRead(notification.id)
                _notifications.value = _notifications.value.orEmpty().map { item ->
                    if (item.id == notification.id) item.copy(isRead = true) else item
                }
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.update_notification_failed
            }
        }
    }

    fun markAllRead() {
        val currentNotifications = _notifications.value.orEmpty()

        if (currentNotifications.none { notification -> !notification.isRead }) return

        viewModelScope.launch {
            try {
                repository.markAllRead()
                _notifications.value = currentNotifications.map { notification ->
                    notification.copy(isRead = true)
                }
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.update_notification_failed
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}
