package com.example.music_app.ui.library

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.databinding.FragmentPlaylistsBinding

class PlaylistsFragment : Fragment(R.layout.fragment_playlists) {

    private var _binding: FragmentPlaylistsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlaylistsViewModel by viewModels()

    private lateinit var adapter: PlaylistAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentPlaylistsBinding.bind(view)

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadPlaylists()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadPlaylists()
    }

    private fun setupRecyclerView() {
        adapter = PlaylistAdapter(
            onItemClick = { playlist ->
                parentFragmentManager.commit {
                    replace(
                        R.id.fragmentContainer,
                        PlaylistDetailFragment.newInstance(
                            playlistId = playlist.id,
                            playlistName = playlist.name
                        )
                    )
                    addToBackStack(null)
                }
            },
            onDeleteClick = { playlist ->
                confirmDeletePlaylist(playlist.id)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnAddPlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }
    }

    private fun observeViewModel() {
        viewModel.playlists.observe(viewLifecycleOwner) { playlists ->
            adapter.setData(playlists)

            binding.tvEmpty.visibility =
                if (playlists.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCreatePlaylistDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Tên playlist"
            setSingleLine(true)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Tạo playlist")
            .setView(input)
            .setPositiveButton("Tạo") { _, _ ->
                val name = input.text.toString().trim()

                if (name.isBlank()) {
                    Toast.makeText(
                        requireContext(),
                        "Tên playlist không được để trống",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    viewModel.createPlaylist(name)
                }
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    private fun confirmDeletePlaylist(playlistId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Xoá playlist")
            .setMessage("Bạn có chắc muốn xoá playlist này không?")
            .setPositiveButton("Xoá") { _, _ ->
                viewModel.deletePlaylist(playlistId)
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}