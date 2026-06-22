package com.example.music_app.ui.playlists

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.databinding.FragmentPlaylistsBinding
import com.example.music_app.data.repository.SoundCloudSocialRepository
import com.example.music_app.data.model.Playlist
import com.example.music_app.ui.library.PlaylistsViewModel
import android.app.Dialog
import android.graphics.Color
import androidx.core.graphics.drawable.toDrawable
import com.example.music_app.databinding.DialogConfirmActionBinding

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
                parentFragmentManager.beginTransaction()
                    .replace(
                        R.id.fragmentContainer,
                        PlaylistDetailFragment.newInstance(
                            playlistId = playlist.id,
                            playlistName = playlist.name,
                            ownerId = playlist.ownerId,
                            coverUrl = playlist.coverUrl
                        )
                    )
                    .addToBackStack(null)
                    .commit()
            },
            onDeleteClick = { playlist ->
                if (SoundCloudSocialRepository.isSoundCloudApiPlaylist(playlist)) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.api_playlist_delete_not_supported),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    confirmDeletePlaylist(playlist)
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

        binding.btnAddPlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }
    }

    private fun observeViewModel() {
        viewModel.playlists.observe(viewLifecycleOwner) { playlists ->
            adapter.setData(playlists)
            binding.tvEmpty.isVisible = playlists.isEmpty()
        }

        viewModel.errorMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                Toast.makeText(requireContext(), getString(it), Toast.LENGTH_SHORT).show()
                viewModel.clearErrorMessage()
            }
        }
    }

    private fun showCreatePlaylistDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.playlist_name_hint)
            setSingleLine(true)
            setPadding(36, 24, 36, 24)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.create_playlist))
            .setIcon(R.drawable.music_orange)
            .setMessage(getString(R.string.create_playlist_message))
            .setView(input)
            .setPositiveButton(getString(R.string.create), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.playlist_name_empty),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    viewModel.createPlaylist(name)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun confirmDeletePlaylist(playlist: Playlist) {
        val dialogBinding = DialogConfirmActionBinding.inflate(layoutInflater)

        val dialog = Dialog(requireContext())
        dialog.setContentView(dialogBinding.root)

        dialogBinding.txtDialogTitle.text = getString(R.string.delete_playlist_title)
        dialogBinding.txtDialogMessage.text =
            getString(R.string.delete_playlist_confirm_with_name, playlist.name)
        dialogBinding.btnConfirm.text = getString(R.string.delete)

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirm.setOnClickListener {
            dialog.dismiss()
            viewModel.deletePlaylist(playlist.id)
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
