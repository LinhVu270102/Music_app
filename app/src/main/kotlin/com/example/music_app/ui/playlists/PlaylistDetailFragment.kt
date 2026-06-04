package com.example.music_app.ui.library

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.FragmentPlaylistDetailBinding
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.player.PlayerFragment
import com.example.music_app.ui.song.SongAdapter

class PlaylistDetailFragment : Fragment(R.layout.fragment_playlist_detail) {

    private var _binding: FragmentPlaylistDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlaylistDetailViewModel by viewModels()

    private lateinit var adapter: SongAdapter

    private var playlistId: String = ""
    private var playlistName: String = ""
    private var currentSongs: List<Song> = emptyList()

    companion object {
        private const val ARG_PLAYLIST_ID = "playlistId"
        private const val ARG_PLAYLIST_NAME = "playlistName"

        fun newInstance(
            playlistId: String,
            playlistName: String
        ): PlaylistDetailFragment {
            return PlaylistDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PLAYLIST_ID, playlistId)
                    putString(ARG_PLAYLIST_NAME, playlistName)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentPlaylistDetailBinding.bind(view)

        playlistId = arguments?.getString(ARG_PLAYLIST_ID).orEmpty()
        playlistName = arguments?.getString(ARG_PLAYLIST_NAME).orEmpty()

        binding.tvPlaylistName.text =
            playlistName.ifBlank { getString(R.string.playlist) }

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadPlaylistSongs(playlistId)
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
                confirmRemoveSong(song)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun observeViewModel() {
        viewModel.songs.observe(viewLifecycleOwner) { songs ->
            currentSongs = songs
            adapter.setData(songs)
            binding.tvEmpty.isVisible = songs.isEmpty()
        }

        viewModel.errorMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                viewModel.clearErrorMessage()
            }
        }
    }

    private fun openPlayer(song: Song) {
        if (currentSongs.isNotEmpty()) {
            PlayerManager.setPlaylist(currentSongs)
        }

        PlayerManager.play(song)

        parentFragmentManager.commit {
            replace(R.id.fragmentContainer, PlayerFragment.newInstance(song.id))
            addToBackStack(null)
        }
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