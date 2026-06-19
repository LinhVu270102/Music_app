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
import androidx.lifecycle.lifecycleScope
import com.example.music_app.R
import com.example.music_app.databinding.FragmentUploadMusicBinding
import com.example.music_app.ui.profile.ProfileFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class UploadMusicFragment : Fragment(R.layout.fragment_upload_music) {

    private var _binding: FragmentUploadMusicBinding? = null
    private val binding get() = _binding!!

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

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentUploadMusicBinding.bind(view)

        setupClickListeners()
    }

    private fun setupClickListeners() {
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
            uploadMusic()
        }
    }

    private fun uploadMusic() {
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.not_logged_in),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val audioUri = selectedAudioUri

        if (audioUri == null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.please_select_audio_file),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val title = binding.edtSongTitle.text.toString().trim()
        val artist = binding.edtArtist.text.toString().trim()
        val genre = binding.edtGenre.text.toString().trim()

        val tags = binding.edtTags.text
            .toString()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (title.isBlank()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.song_title_required),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        lifecycleScope.launch {
            setLoading(true)

            try {
                val songId = FirebaseFirestore.getInstance()
                    .collection("songs")
                    .document()
                    .id

                val audioExtension = getExtension(
                    uri = audioUri,
                    fallback = "mp3"
                )

                val audioPath = "songs/${currentUser.uid}/$songId.$audioExtension"

                val songUrl = withContext(Dispatchers.IO) {
                    uploadFile(
                        uri = audioUri,
                        path = audioPath
                    )
                }

                val coverUrl = selectedCoverUri?.let { coverUri ->
                    val coverExtension = getExtension(
                        uri = coverUri,
                        fallback = "jpg"
                    )

                    val coverPath = "covers/${currentUser.uid}/$songId.$coverExtension"

                    withContext(Dispatchers.IO) {
                        uploadFile(
                            uri = coverUri,
                            path = coverPath
                        )
                    }
                }.orEmpty()

                withContext(Dispatchers.IO) {
                    saveSongToFirestore(
                        songId = songId,
                        title = title,
                        artist = artist.ifBlank {
                            currentUser.displayName
                                ?: currentUser.email
                                ?: getString(R.string.unknown_artist)
                        },
                        genre = genre,
                        tags = tags,
                        songUrl = songUrl,
                        coverUrl = coverUrl,
                        uploaderId = currentUser.uid
                    )
                }

                Toast.makeText(
                    requireContext(),
                    getString(R.string.upload_music_success),
                    Toast.LENGTH_SHORT
                ).show()

                openProfile()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    e.message ?: getString(R.string.upload_music_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private suspend fun uploadFile(
        uri: Uri,
        path: String
    ): String {
        val storageRef = FirebaseStorage.getInstance()
            .reference
            .child(path)

        storageRef.putFile(uri).await()

        return storageRef.downloadUrl.await().toString()
    }

    private suspend fun saveSongToFirestore(
        songId: String,
        title: String,
        artist: String,
        genre: String,
        tags: List<String>,
        songUrl: String,
        coverUrl: String,
        uploaderId: String
    ) {
        val now = System.currentTimeMillis()

        val songData = hashMapOf<String, Any?>(
            "id" to songId,
            "title" to title,
            "artist" to artist,
            "coverUrl" to coverUrl,
            "songUrl" to songUrl,
            "duration" to 0,
            "plays" to 0L,
            "likes" to 0L,
            "commentsCount" to 0L,
            "uploaderId" to uploaderId,
            "genre" to genre,
            "tags" to tags,
            "status" to "PENDING",
            "rejectReason" to "",
            "reviewedBy" to "",
            "reviewedAt" to null,
            "reportsCount" to 0L,
            "allowComments" to true,
            "isDeleted" to false,
            "deletedAt" to null,
            "deletedBy" to "",
            "createdAt" to now,
            "updatedAt" to now
        )

        FirebaseFirestore.getInstance()
            .collection("songs")
            .document(songId)
            .set(songData)
            .await()
    }

    private fun openProfile() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, ProfileFragment())
            .commit()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility =
            if (isLoading) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.btnUploadMusic.isEnabled = !isLoading
        binding.btnPickAudio.isEnabled = !isLoading
        binding.btnPickCover.isEnabled = !isLoading
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null

        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            requireContext().contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }
        }

        return result
            ?: uri.lastPathSegment
            ?: getString(R.string.selected_file)
    }

    private fun getExtension(
        uri: Uri,
        fallback: String
    ): String {
        val mimeType = requireContext().contentResolver.getType(uri)

        return MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType)
            ?: fallback
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}