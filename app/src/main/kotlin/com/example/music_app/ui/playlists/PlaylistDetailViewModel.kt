package com.example.music_app.ui.library

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.utils.AppException
import kotlinx.coroutines.launch
import com.example.music_app.data.repository.SoundCloudSocialRepository

class PlaylistDetailViewModel : ViewModel() {

    private val repository = SongRepository()
    private val soundCloudSocialRepository = SoundCloudSocialRepository()

    private val _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> = _songs

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    fun loadPlaylistSongs(
        playlistId: String,
        ownerId: String = ""
    ) {
        viewModelScope.launch {
            try {
                _songs.value =
                    if (
                        SoundCloudSocialRepository.isSoundCloudApiPlaylist(
                            playlistId = playlistId,
                            ownerId = ownerId
                        )
                    ) {
                        val firestoreSongs = repository.getRootPlaylistSongs(playlistId)

                        firestoreSongs.ifEmpty {
                            soundCloudSocialRepository.getUserApiPlaylistSongs(playlistId)
                        }
                    } else {
                        repository.getPlaylistSongs(
                            ownerId = ownerId,
                            playlistId = playlistId
                        )
                    }
            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.load_playlist_songs_failed
            }
        }
    }

    fun removeSongFromPlaylist(
        playlistId: String,
        songId: String,
        ownerId: String = ""
    ) {
        viewModelScope.launch {
            try {
                if (
                    SoundCloudSocialRepository.isSoundCloudApiPlaylist(
                        playlistId = playlistId,
                        ownerId = ownerId
                    )
                ) {
                    soundCloudSocialRepository.removeTrackFromUserApiPlaylist(
                        playlistId = playlistId,
                        songId = songId
                    )
                } else {
                    repository.removeSongFromPlaylist(
                        playlistId = playlistId,
                        songId = songId
                    )
                }

                loadPlaylistSongs(
                    playlistId = playlistId,
                    ownerId = ownerId
                )
            } catch (e: AppException) {
                _errorMessageResId.value = e.messageResId
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.remove_song_from_playlist_failed
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }
}