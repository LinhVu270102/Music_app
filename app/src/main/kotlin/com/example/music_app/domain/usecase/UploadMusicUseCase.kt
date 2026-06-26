package com.example.music_app.domain.usecase

import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.UploadMusicRepository
import com.example.music_app.data.repository.UploadMusicRequest

/** One application action: persist an uploaded song and its assets. */
class UploadMusicUseCase(
    private val uploadRepository: UploadMusicRepository = UploadMusicRepository()
) {
    suspend operator fun invoke(request: UploadMusicRequest): Song {
        return uploadRepository.uploadMusic(request)
    }
}
