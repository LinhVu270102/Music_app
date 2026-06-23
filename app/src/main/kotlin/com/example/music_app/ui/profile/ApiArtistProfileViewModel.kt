package com.example.music_app.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.remote.soundcloud.model.SoundCloudArtistProfileDto
import com.example.music_app.data.remote.soundcloud.toSong
import com.example.music_app.data.repository.SoundCloudRepository
import kotlinx.coroutines.launch

class ApiArtistProfileViewModel : ViewModel() {

    private val soundCloudRepository = SoundCloudRepository()

    private val _profile = MutableLiveData<SoundCloudArtistProfileDto>()
    val profile: LiveData<SoundCloudArtistProfileDto> = _profile

    private val _songs = MutableLiveData<List<Song>>(emptyList())
    val songs: LiveData<List<Song>> = _songs

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    fun loadArtistProfile(
        artistName: String,
        limit: Int = 20
    ) {
        if (artistName.isBlank()) {
            _errorMessageResId.value = R.string.unknown_artist
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true

                val response = soundCloudRepository.getArtistProfile(artistName, limit)

                _profile.value = response.profile
                _songs.value = response.results.map { track ->
                    track.toSong()
                }
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.open_artist_profile_failed
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}
