package com.example.music_app.ui.comment

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.data.model.Comment
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.FragmentCommentBinding
import com.example.music_app.main.MainActivity
import com.google.firebase.auth.FirebaseAuth
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import com.example.music_app.databinding.DialogCommentOptionsBinding
import com.example.music_app.databinding.DialogReportCommentBinding
import com.example.music_app.player.PlayerManager
class CommentFragment : Fragment(R.layout.fragment_comment) {

    private var _binding: FragmentCommentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CommentViewModel by viewModels()
    private lateinit var adapter: CommentAdapter

    private var songId: String = ""
    private var currentSong: Song? = null

    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    companion object {
        private const val ARG_SONG_ID = "songId"

        private const val MENU_REPORT_COMMENT = 1
        private const val MENU_HIDE_COMMENT = 2

        fun newInstance(songId: String): CommentFragment {
            return CommentFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SONG_ID, songId)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentCommentBinding.bind(view)

        (requireActivity() as? MainActivity)?.updateMainChromeVisibility()

        songId = arguments?.getString(ARG_SONG_ID).orEmpty()

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadSong(songId)
        viewModel.loadComments(songId)
    }

    private fun setupRecyclerView() {
        adapter = CommentAdapter(
            onMoreClick = { comment, anchor ->
                showCommentOptions(comment, anchor)
            }
        )

        binding.rvComments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvComments.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnSendComment.setOnClickListener {
            val song = currentSong

            if (song != null && !song.allowComments) {
                showToast(getString(R.string.comments_locked))
                return@setOnClickListener
            }

            val content = binding.edtComment.text.toString().trim()

            if (content.isBlank()) {
                showToast(getString(R.string.comment_input_empty))
                return@setOnClickListener
            }

            val timelinePositionMs =
                if (PlayerManager.currentSong.value?.id == songId) {
                    PlayerManager.getCurrentPosition()
                } else {
                    0L
                }

            viewModel.addComment(
                songId = songId,
                content = content,
                timelinePositionMs = timelinePositionMs
            )

            binding.edtComment.text?.clear()
        }
    }

    private fun observeViewModel() {
        viewModel.song.observe(viewLifecycleOwner) { song ->
            currentSong = song
            updateCommentInputState(song)
        }

        viewModel.comments.observe(viewLifecycleOwner) { comments ->
            adapter.setData(comments)
        }

        viewModel.errorMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                viewModel.clearErrorMessage()
            }
        }

        viewModel.successMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                viewModel.clearSuccessMessage()
            }
        }
    }

    private fun updateCommentInputState(song: Song?) {
        val allowComments = song?.allowComments != false

        binding.edtComment.isEnabled = allowComments
        binding.btnSendComment.isEnabled = allowComments
        binding.tvCommentsLocked.isVisible = !allowComments
    }

    private fun showCommentOptions(
        comment: Comment,
        anchor: View
    ) {
        val dialogBinding = DialogCommentOptionsBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.txtCommentPreview.text = comment.content.ifBlank {
            getString(R.string.no_report_description)
        }

        dialogBinding.actionHideComment.visibility =
            if (canHideComment(comment)) {
                View.VISIBLE
            } else {
                View.GONE
            }

        dialogBinding.actionReportComment.setOnClickListener {
            dialog.dismiss()
            showReportCommentDialog(comment)
        }

        dialogBinding.actionHideComment.setOnClickListener {
            dialog.dismiss()
            confirmHideComment(comment)
        }

        dialog.show()
    }

    private fun canHideComment(comment: Comment): Boolean {
        val songOwnerId = currentSong?.uploaderId.orEmpty()

        return comment.userId == currentUserId ||
                songOwnerId == currentUserId
    }

    private fun showReportCommentDialog(comment: Comment) {
        val dialogBinding = DialogReportCommentBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.txtReportedCommentPreview.text =
            getString(
                R.string.report_comment_content_format,
                comment.displayName.ifBlank { getString(R.string.unknown_user) },
                comment.content
            )

        dialogBinding.radioCommentSpam.isChecked = true

        dialogBinding.btnCancelCommentReport.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSubmitCommentReport.setOnClickListener {
            val reason = when (dialogBinding.radioCommentReportReasons.checkedRadioButtonId) {
                R.id.radioCommentSpam -> getString(R.string.report_reason_spam)
                R.id.radioCommentOffensive -> getString(R.string.report_reason_offensive)
                R.id.radioCommentHarassment -> getString(R.string.report_reason_harassment)
                R.id.radioCommentOther -> getString(R.string.report_reason_other)
                else -> ""
            }

            if (reason.isBlank()) {
                showToast(getString(R.string.report_reason_empty))
                return@setOnClickListener
            }

            val description = dialogBinding.edtCommentReportDescription.text
                ?.toString()
                ?.trim()
                .orEmpty()

            dialog.dismiss()

            viewModel.reportComment(
                songId = songId,
                comment = comment,
                reason = reason,
                description = description
            )
        }

        dialog.show()
    }

    private fun confirmHideComment(comment: Comment) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.hide_comment))
            .setMessage(getString(R.string.hide_comment_confirm))
            .setPositiveButton(getString(R.string.hide)) { _, _ ->
                viewModel.hideComment(songId, comment)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? MainActivity)?.updateMainChromeVisibility()
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}