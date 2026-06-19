package com.example.music_app.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.FragmentAdminModerationBinding

class AdminModerationFragment : Fragment(R.layout.fragment_admin_moderation) {

    private var _binding: FragmentAdminModerationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminModerationViewModel by viewModels()

    private lateinit var adapter: AdminSongModerationAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentAdminModerationBinding.bind(view)

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadPendingSongs()
    }

    private fun setupRecyclerView() {
        adapter = AdminSongModerationAdapter(
            onApprove = { song ->
                viewModel.approveSong(song)
            },
            onReject = { song ->
                showRejectDialog(song)
            },
            onHide = { song ->
                viewModel.hideSong(song)
            },
            onToggleComments = { song ->
                viewModel.toggleComments(song)
            }
        )

        binding.recyclerPendingSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPendingSongs.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnReload.setOnClickListener {
            viewModel.loadPendingSongs()
        }
    }

    private fun observeViewModel() {
        viewModel.pendingSongs.observe(viewLifecycleOwner) { songs ->
            adapter.submitList(songs)

            binding.txtEmptyPending.visibility =
                if (songs.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressLoading.visibility =
                if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.messageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                Toast.makeText(requireContext(), getString(it), Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }
    }

    private fun showRejectDialog(song: Song) {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.enter_reject_reason)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.reject_reason))
            .setView(input)
            .setPositiveButton(getString(R.string.reject)) { _, _ ->
                viewModel.rejectSong(song, input.text.toString())
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}