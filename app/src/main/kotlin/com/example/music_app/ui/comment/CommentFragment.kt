package com.example.music_app.ui.comment

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
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

            viewModel.addComment(songId, content)
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
        val popup = PopupMenu(requireContext(), anchor)

        popup.menu.add(
            0,
            MENU_REPORT_COMMENT,
            0,
            getString(R.string.report_comment)
        )

        if (canHideComment(comment)) {
            popup.menu.add(
                0,
                MENU_HIDE_COMMENT,
                1,
                getString(R.string.hide_comment)
            )
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_REPORT_COMMENT -> {
                    showReportCommentDialog(comment)
                    true
                }

                MENU_HIDE_COMMENT -> {
                    confirmHideComment(comment)
                    true
                }

                else -> false
            }
        }

        popup.show()
    }

    private fun canHideComment(comment: Comment): Boolean {
        val songOwnerId = currentSong?.uploaderId.orEmpty()

        return comment.userId == currentUserId ||
                songOwnerId == currentUserId
    }

    private fun showReportCommentDialog(comment: Comment) {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.enter_report_reason)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            maxLines = 5
            setPadding(32, 24, 32, 24)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.report_comment))
            .setView(input)
            .setPositiveButton(getString(R.string.report)) { _, _ ->
                viewModel.reportComment(
                    songId = songId,
                    comment = comment,
                    reason = input.text.toString()
                )
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
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