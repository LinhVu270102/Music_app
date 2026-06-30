package com.example.music_app.ui.playlists

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.data.model.Playlist
import com.example.music_app.databinding.DialogConfirmActionBinding
import com.example.music_app.databinding.DialogInputActionBinding
import com.example.music_app.databinding.FragmentPlaylistsBinding
import com.example.music_app.ui.common.showCustomDialog

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
                openPlaylistDetail(playlist)
            },
            onDeleteClick = { playlist ->
                if (viewModel.canDeletePlaylist(playlist)) {
                    confirmDeletePlaylist(playlist)
                }
            },
            canDelete = viewModel::canDeletePlaylist
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        binding.swipeRefreshPlaylists.setOnRefreshListener {
            viewModel.loadPlaylists()
        }

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
            stopRefreshing()
        }

        viewModel.errorMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                viewModel.clearErrorMessage()
                stopRefreshing()
            }
        }
    }

    private fun openPlaylistDetail(playlist: Playlist) {
        parentFragmentManager.commit {
            replace(
                R.id.fragmentContainer,
                PlaylistDetailFragment.newInstance(
                    playlistId = playlist.id,
                    playlistName = playlist.name,
                    ownerId = playlist.ownerId,
                    coverUrl = playlist.coverUrl
                )
            )
            addToBackStack(null)
        }
    }

    private fun showCreatePlaylistDialog() {
        val dialogBinding = DialogInputActionBinding.inflate(layoutInflater)
        val dialog = createDialog(dialogBinding.root)

        dialogBinding.txtDialogTitle.text = getString(R.string.create_playlist)
        dialogBinding.txtDialogMessage.text = getString(R.string.create_playlist_message)
        dialogBinding.edtDialogInput.hint = getString(R.string.playlist_name_hint)
        dialogBinding.btnConfirm.text = getString(R.string.create)
        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnConfirm.setOnClickListener {
            val name = dialogBinding.edtDialogInput.text.toString().trim()
            if (name.isBlank()) {
                showToast(getString(R.string.playlist_name_empty))
                return@setOnClickListener
            }

            viewModel.createPlaylist(name)
            dialog.dismiss()
        }

        dialog.showCustomDialog()
    }

    private fun confirmDeletePlaylist(playlist: Playlist) {
        val dialogBinding = DialogConfirmActionBinding.inflate(layoutInflater)
        val dialog = createDialog(dialogBinding.root)

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

        dialog.showCustomDialog()
    }

    private fun createDialog(contentView: View): Dialog {
        return Dialog(requireContext()).apply {
            setContentView(contentView)
        }
    }

    private fun stopRefreshing() {
        binding.swipeRefreshPlaylists.isRefreshing = false
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
