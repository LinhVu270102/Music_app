package com.example.music_app.ui.yourupload

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.music_app.R
import com.example.music_app.databinding.FragmentUploadMusicBinding
import com.example.music_app.ui.profile.ProfileFragment

class UploadMusicFragment : Fragment(R.layout.fragment_upload_music) {

    private var _binding: FragmentUploadMusicBinding? = null
    private val binding get() = _binding!!

    private val viewModel: UploadMusicViewModel by viewModels()

    private var selectedAudioUri: Uri? = null
    private var selectedCoverUri: Uri? = null

    private val pickAudioLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult

            selectedAudioUri = uri
            binding.txtSelectedAudio.text = getFileName(uri)
        }

    private val pickCoverLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult

            selectedCoverUri = uri
            binding.txtSelectedCover.text = getFileName(uri)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentUploadMusicBinding.bind(view)

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnPickAudio.setOnClickListener {
            pickAudioLauncher.launch("audio/*")
        }

        binding.btnPickCover.setOnClickListener {
            pickCoverLauncher.launch("image/*")
        }

        binding.btnUploadMusic.setOnClickListener {
            submitUpload()
        }
    }

    private fun observeViewModel() {
        viewModel.uploadState.observe(viewLifecycleOwner) { state ->
            when (state) {
                UploadMusicUiState.Idle -> setLoading(false)
                UploadMusicUiState.Loading -> setLoading(true)
                UploadMusicUiState.Success -> {
                    setLoading(false)
                    showToast(getString(R.string.upload_music_success))
                    viewModel.consumeUploadState()
                    openProfile()
                }

                is UploadMusicUiState.Error -> {
                    setLoading(false)
                    showToast(
                        state.messageResId?.let(::getString)
                            ?: state.message
                            ?: getString(R.string.upload_music_failed)
                    )
                    viewModel.consumeUploadState()
                }
            }
        }
    }

    private fun submitUpload() {
        val audioUri = selectedAudioUri
        val coverUri = selectedCoverUri

        viewModel.uploadMusic(
            audioUri = audioUri,
            coverUri = coverUri,
            audioExtension = audioUri?.let { getExtension(it, "mp3") } ?: "mp3",
            coverExtension = coverUri?.let { getExtension(it, "jpg") },
            title = binding.edtSongTitle.text.toString().trim(),
            artist = binding.edtArtist.text.toString().trim(),
            genre = binding.edtGenre.text.toString().trim(),
            tags = binding.edtTags.text.toString()
                .split(",")
                .map(String::trim)
                .filter(String::isNotBlank),
            unknownArtist = getString(R.string.unknown_artist)
        )
    }

    private fun openProfile() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, ProfileFragment())
            .commit()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnUploadMusic.isEnabled = !isLoading
        binding.btnPickAudio.isEnabled = !isLoading
        binding.btnPickCover.isEnabled = !isLoading
    }

    private fun getFileName(uri: Uri): String {
        var fileName: String? = null

        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) fileName = cursor.getString(index)
                }
            }
        }

        return fileName ?: uri.lastPathSegment ?: getString(R.string.selected_file)
    }

    private fun getExtension(
        uri: Uri,
        fallback: String
    ): String {
        val mimeType = requireContext().contentResolver.getType(uri)
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: fallback
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
