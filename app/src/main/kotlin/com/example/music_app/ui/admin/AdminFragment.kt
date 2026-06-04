package com.example.music_app.ui.admin

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.SongStatus
import com.example.music_app.databinding.FragmentAdminBinding
import com.example.music_app.player.PlayerManager

class AdminFragment : Fragment(R.layout.fragment_admin) {

    private var _binding: FragmentAdminBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminViewModel by viewModels()

    private lateinit var adapter: AdminModerationAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentAdminBinding.bind(view)

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadSongs(SongStatus.PENDING)
    }

    private fun setupRecyclerView() {
        adapter = AdminModerationAdapter(
            onPreviewClick = { song ->
                previewSong(song)
            },
            onApproveClick = { song ->
                viewModel.approveSong(song.id)
            },
            onRejectClick = { song ->
                showRejectDialog(song)
            },
            onHideClick = { song ->
                viewModel.hideSong(song.id)
            },
            onRestoreClick = { song ->
                viewModel.restoreSong(song.id)
            }
        )

        binding.recyclerAdminSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerAdminSongs.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnRefresh.setOnClickListener {
            viewModel.loadSongs()
        }

        binding.btnPending.setOnClickListener {
            viewModel.loadSongs(SongStatus.PENDING)
        }

        binding.btnApproved.setOnClickListener {
            viewModel.loadSongs(SongStatus.APPROVED)
        }

        binding.btnRejected.setOnClickListener {
            viewModel.loadSongs(SongStatus.REJECTED)
        }

        binding.btnHidden.setOnClickListener {
            viewModel.loadSongs(SongStatus.HIDDEN)
        }
    }

    private fun observeViewModel() {
        viewModel.songs.observe(viewLifecycleOwner) { songs ->
            adapter.submitList(songs)

            binding.txtSongCount.text = songs.size.toString()
            binding.emptyState.visibility = if (songs.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        viewModel.selectedStatus.observe(viewLifecycleOwner) { status ->
            updateStatusButtons(status)
        }

        viewModel.messageRes.observe(viewLifecycleOwner) { messageRes ->
            messageRes?.let {
                Toast.makeText(requireContext(), getString(it), Toast.LENGTH_SHORT).show()
                viewModel.doneShowMessage()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    private fun updateStatusButtons(status: String) {
        binding.btnPending.isSelected = status == SongStatus.PENDING
        binding.btnApproved.isSelected = status == SongStatus.APPROVED
        binding.btnRejected.isSelected = status == SongStatus.REJECTED
        binding.btnHidden.isSelected = status == SongStatus.HIDDEN
    }

    private fun previewSong(song: Song) {
        PlayerManager.play(song)

        Toast.makeText(
            requireContext(),
            getString(R.string.previewing_song),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showRejectDialog(song: Song) {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.enter_reject_reason)
            setSingleLine(false)
            minLines = 3
            maxLines = 5
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.reject_song))
            .setMessage(getString(R.string.reject_song_message))
            .setView(input)
            .setPositiveButton(getString(R.string.reject)) { _, _ ->
                val reason = input.text.toString().trim()
                viewModel.rejectSong(song.id, reason)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}