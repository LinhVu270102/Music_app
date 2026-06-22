package com.example.music_app.ui.playlists

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import com.example.music_app.R
import com.example.music_app.data.model.PlaylistPickerItem
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.databinding.FragmentPlaylistDetailBinding
import com.example.music_app.ui.player.PlaybackLauncher
import com.example.music_app.ui.song.SongAdapter
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.example.music_app.data.repository.SoundCloudSocialRepository
import com.example.music_app.ui.library.PlaylistDetailViewModel
import android.graphics.Color
import androidx.core.graphics.drawable.toDrawable
import com.example.music_app.databinding.DialogConfirmActionBinding
import com.example.music_app.databinding.DialogInputActionBinding
import com.example.music_app.databinding.DialogSelectPlaylistBinding
import com.example.music_app.ui.player.PlaylistPickerAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistDetailFragment : Fragment(R.layout.fragment_playlist_detail) {

    private var _binding: FragmentPlaylistDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlaylistDetailViewModel by viewModels()
    private val songRepository = SongRepository()

    private lateinit var adapter: SongAdapter

    private var playlistId: String = ""
    private var playlistName: String = ""
    private var currentSongs: List<Song> = emptyList()
    private var ownerId: String = ""
    private var coverUrl: String = ""
    private fun isOwnerPlaylist(): Boolean {
        if (isSoundCloudApiPlaylist()) return false

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val finalOwnerId = ownerId.ifBlank { currentUserId }

        return currentUserId.isNotBlank() && currentUserId == finalOwnerId
    }

    companion object {
        private const val ARG_PLAYLIST_ID = "playlistId"
        private const val ARG_PLAYLIST_NAME = "playlistName"
        private const val ARG_OWNER_ID = "ownerId"
        private const val ARG_COVER_URL = "coverUrl"

        fun newInstance(
            playlistId: String,
            playlistName: String,
            ownerId: String = "",
            coverUrl: String = ""
        ): PlaylistDetailFragment {
            return PlaylistDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PLAYLIST_ID, playlistId)
                    putString(ARG_PLAYLIST_NAME, playlistName)
                    putString(ARG_OWNER_ID, ownerId)
                    putString(ARG_COVER_URL, coverUrl)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ownerId = arguments?.getString(ARG_OWNER_ID).orEmpty()
        coverUrl = arguments?.getString(ARG_COVER_URL).orEmpty()
        _binding = FragmentPlaylistDetailBinding.bind(view)

        playlistId = arguments?.getString(ARG_PLAYLIST_ID).orEmpty()
        playlistName = arguments?.getString(ARG_PLAYLIST_NAME).orEmpty()

        binding.tvPlaylistName.text =
            playlistName.ifBlank { getString(R.string.playlist) }
        binding.tvPlaylistSubtitle.text =
            if (isSoundCloudApiPlaylist()) {
                getString(R.string.soundcloud_api_playlist_subtitle)
            } else {
                getString(R.string.playlist_detail_subtitle)
            }

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadPlaylistSongs(
            playlistId = playlistId,
            ownerId = ownerId
        )
    }

    override fun onResume() {
        super.onResume()

        if (playlistId.isNotBlank()) {
            viewModel.loadPlaylistSongs(
                playlistId = playlistId,
                ownerId = ownerId
            )
        }
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter(
            onItemClick = { song ->
                openPlayer(song)
            },
            onItemLongClick = { song ->
                if (isOwnerPlaylist()) {
                    confirmRemoveSong(song)
                }
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnPlayAll.setOnClickListener {
            val firstSong = currentSongs.firstOrNull()

            if (firstSong == null) {
                showToast(getString(R.string.no_songs_in_playlist))
            } else {
                openPlayer(firstSong)
            }
        }

        binding.btnSavePlaylist.setOnClickListener {
            showSavePlaylistDialog()
        }
    }

    private fun observeViewModel() {
        viewModel.songs.observe(viewLifecycleOwner) { songs ->
            currentSongs = songs
            adapter.setData(songs)

            binding.tvEmpty.isVisible = songs.isEmpty()
            binding.btnSavePlaylist.isVisible =
                !isOwnerPlaylist() && songs.isNotEmpty()
            binding.tvSongCount.text =
                getString(R.string.playlist_song_count_format, songs.size)

            val finalCoverUrl = coverUrl.ifBlank {
                songs.firstOrNull()?.coverUrl.orEmpty()
            }

            Glide.with(this)
                .load(finalCoverUrl.ifBlank { R.drawable.music_orange })
                .placeholder(R.drawable.music_orange)
                .error(R.drawable.music_orange)
                .centerCrop()
                .into(binding.imgPlaylistCover)
        }

        viewModel.errorMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                viewModel.clearErrorMessage()
            }
        }
    }

    private fun openPlayer(song: Song) {
        PlaybackLauncher.openPlayer(
            fragment = this,
            song = song,
            playlist = currentSongs
        )
    }

    private fun confirmRemoveSong(song: Song) {
        val dialogBinding = DialogConfirmActionBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        dialogBinding.txtDialogTitle.text = getString(R.string.remove_from_playlist_title)
        dialogBinding.txtDialogMessage.text =
            getString(R.string.remove_song_from_playlist_confirm, song.title)
        dialogBinding.btnConfirm.text = getString(R.string.delete)

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirm.setOnClickListener {
            dialog.dismiss()
            viewModel.removeSongFromPlaylist(
                playlistId = playlistId,
                songId = song.id,
                ownerId = ownerId
            )
        }

        dialog.show()
    }

    private fun showSavePlaylistDialog() {
        if (currentSongs.isEmpty()) {
            showToast(getString(R.string.no_songs_in_playlist))
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val playlists = withContext(Dispatchers.IO) {
                    songRepository.getMyPlaylists()
                }

                if (playlists.isEmpty()) {
                    showCreatePlaylistThenSave()
                    return@launch
                }

                val dialogBinding = DialogSelectPlaylistBinding.inflate(layoutInflater)
                val dialog = AlertDialog.Builder(requireContext())
                    .setView(dialogBinding.root)
                    .create()

                dialogBinding.txtDialogTitle.text = getString(R.string.save_playlist_to_mine)
                dialogBinding.txtDialogSubtitle.text =
                    getString(R.string.save_playlist_dialog_subtitle)

                val adapter = PlaylistPickerAdapter { item ->
                    dialog.dismiss()
                    addSongsToMyPlaylist(item.id)
                }

                dialogBinding.rvPlaylists.layoutManager = LinearLayoutManager(requireContext())
                dialogBinding.rvPlaylists.adapter = adapter
                adapter.setData(
                    playlists.map { playlist ->
                        PlaylistPickerItem(
                            id = playlist.id,
                            name = playlist.name,
                            subtitle = getString(
                                R.string.playlist_songs_count,
                                playlist.songsCount
                            )
                        )
                    }
                )

                dialogBinding.btnCreatePlaylist.setOnClickListener {
                    dialog.dismiss()
                    showCreatePlaylistThenSave()
                }
                dialogBinding.btnClose.setOnClickListener { dialog.dismiss() }

                dialog.show()
                dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            } catch (_: Exception) {
                showToast(getString(R.string.load_playlists_failed))
            }
        }
    }

    private fun showCreatePlaylistThenSave() {
        val dialogBinding = DialogInputActionBinding.inflate(layoutInflater)
        val dialog = Dialog(requireContext())
        dialog.setContentView(dialogBinding.root)

        dialogBinding.txtDialogTitle.text = getString(R.string.create_playlist)
        dialogBinding.txtDialogMessage.text = getString(R.string.create_saved_playlist_message)
        dialogBinding.edtDialogInput.hint = getString(R.string.playlist_name_hint)
        dialogBinding.btnConfirm.text = getString(R.string.create)
        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnConfirm.setOnClickListener {
            val name = dialogBinding.edtDialogInput.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                showToast(getString(R.string.playlist_name_empty))
                return@setOnClickListener
            }

            dialog.dismiss()
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val playlist = withContext(Dispatchers.IO) {
                        songRepository.createPlaylist(name)
                    }
                    addSongsToMyPlaylist(playlist.id)
                } catch (_: Exception) {
                    showToast(getString(R.string.create_playlist_failed))
                }
            }
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    }

    private fun addSongsToMyPlaylist(targetPlaylistId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    currentSongs.forEach { song ->
                        songRepository.addSongToPlaylist(targetPlaylistId, song)
                    }
                }
                showToast(getString(R.string.save_playlist_success))
            } catch (_: Exception) {
                showToast(getString(R.string.save_playlist_failed))
            }
        }
    }
    private fun isSoundCloudApiPlaylist(): Boolean {
        return SoundCloudSocialRepository.isSoundCloudApiPlaylist(
            playlistId = playlistId,
            ownerId = ownerId
        )
    }
    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
