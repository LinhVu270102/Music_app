package com.example.music_app.ui.library

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.FragmentPlaylistDetailBinding
import com.example.music_app.ui.player.PlaybackLauncher
import com.example.music_app.ui.song.SongAdapter
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth

class PlaylistDetailFragment : Fragment(R.layout.fragment_playlist_detail) {

    private var _binding: FragmentPlaylistDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlaylistDetailViewModel by viewModels()

    private lateinit var adapter: SongAdapter

    private var playlistId: String = ""
    private var playlistName: String = ""
    private var currentSongs: List<Song> = emptyList()
    private var ownerId: String = ""
    private var coverUrl: String = ""
    private fun isOwnerPlaylist(): Boolean {
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
            viewModel.loadPlaylistSongs(playlistId)
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
    }

    private fun observeViewModel() {
        viewModel.songs.observe(viewLifecycleOwner) { songs ->
            currentSongs = songs
            adapter.setData(songs)

            binding.tvEmpty.isVisible = songs.isEmpty()
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
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.remove_from_playlist_title))
            .setMessage(getString(R.string.remove_song_from_playlist_confirm, song.title))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.removeSongFromPlaylist(
                    playlistId = playlistId,
                    songId = song.id
                )
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}