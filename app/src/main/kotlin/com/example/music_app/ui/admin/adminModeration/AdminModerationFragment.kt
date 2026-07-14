package com.example.music_app.ui.admin.adminModeration

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.enums.FingerprintStatus
import com.example.music_app.databinding.DialogConfirmActionBinding
import com.example.music_app.databinding.FragmentAdminModerationBinding
import com.example.music_app.databinding.DialogInputActionBinding
import com.example.music_app.ui.admin.adminSongModeration.AdminSongModerationAdapter
import com.example.music_app.ui.common.showCustomDialog
import java.util.Locale

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
                handleApprove(song)
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
        binding.swipeRefreshAdminModeration.setOnRefreshListener {
            viewModel.loadPendingSongs()
        }

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

    }

    private fun handleApprove(song: Song) {
        when (song.fingerprintStatusType) {
            FingerprintStatus.UNIQUE -> viewModel.approveSong(song)

            FingerprintStatus.PENDING,
            FingerprintStatus.PROCESSING -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.approve_blocked_audio_check_pending),
                    Toast.LENGTH_SHORT
                ).show()
            }

            FingerprintStatus.DUPLICATE -> showApproveWarningDialog(
                song = song,
                title = getString(R.string.approve_duplicate_warning_title),
                message = getString(
                    R.string.approve_duplicate_warning_message,
                    song.duplicateOfSongId.ifBlank {
                        getString(R.string.fingerprint_duplicate_unknown_target)
                    },
                    song.duplicateScore.asPercent()
                )
            )

            FingerprintStatus.FAILED -> showApproveWarningDialog(
                song = song,
                title = getString(R.string.approve_failed_warning_title),
                message = getString(
                    R.string.approve_failed_warning_message,
                    song.fingerprintError.ifBlank {
                        getString(R.string.fingerprint_failed)
                    }
                )
            )
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
            binding.swipeRefreshAdminModeration.isRefreshing = isLoading
        }

        viewModel.messageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                Toast.makeText(requireContext(), getString(it), Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
                binding.swipeRefreshAdminModeration.isRefreshing = false
            }
        }
    }

    private fun showRejectDialog(song: Song) {
        val dialogBinding = DialogInputActionBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.txtDialogTitle.text = getString(R.string.reject_reason)
        dialogBinding.txtDialogMessage.text = getString(R.string.enter_reject_reason)
        dialogBinding.edtDialogInput.hint = getString(R.string.enter_reject_reason)
        dialogBinding.btnConfirm.text = getString(R.string.reject)

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirm.setOnClickListener {
            val reason = dialogBinding.edtDialogInput.text.toString().trim()

            if (reason.isBlank()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.enter_reject_reason),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            dialog.dismiss()
            viewModel.rejectSong(song, reason)
        }

        dialog.showCustomDialog()
    }

    private fun showApproveWarningDialog(
        song: Song,
        title: String,
        message: String
    ) {
        val dialogBinding = DialogConfirmActionBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.txtDialogTitle.text = title
        dialogBinding.txtDialogMessage.text = message
        dialogBinding.btnConfirm.text = getString(R.string.approve_anyway)

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirm.setOnClickListener {
            dialog.dismiss()
            viewModel.approveSong(song)
        }

        dialog.showCustomDialog()
    }

    private fun Double.asPercent(): String {
        val normalizedScore = if (this <= 1.0) this * 100 else this
        return String.format(Locale.US, "%.0f%%", normalizedScore.coerceIn(0.0, 100.0))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
