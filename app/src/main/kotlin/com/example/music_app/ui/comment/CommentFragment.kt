package com.example.music_app.ui.comment

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
import com.example.music_app.player.PlayerManager
import androidx.lifecycle.lifecycleScope
import com.example.music_app.ui.player.PlaybackLauncher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CommentFragment : Fragment(R.layout.fragment_comment) {

    private var _binding: FragmentCommentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CommentViewModel by viewModels()
    private lateinit var adapter: CommentAdapter
    private lateinit var dialogController: CommentDialogController

    private var songId: String = ""
    private var currentSong: Song? = null

    private var activeUserId: String = ""

    companion object {
        private const val ARG_SONG_ID = "songId"

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
        activeUserId = viewModel.getCurrentUserId()

        setupDialogController()
        setupRecyclerView()
        setupListeners()
        observeViewModel()

        val fallbackSong =
            PlayerManager.currentSong.value?.takeIf { song ->
                song.id == songId
            }

        // Clear comment cũ trước khi load lại
        adapter.setData(emptyList())

        viewModel.loadSong(
            songId = songId,
            fallbackSong = fallbackSong
        )

        viewModel.loadComments(songId)
    }

    private fun setupDialogController() {
        dialogController = CommentDialogController(
            fragment = this,
            currentUserId = viewModel::getCurrentUserId,
            songOwnerId = { currentSong?.uploaderId.orEmpty() },
            onReportComment = { comment, reason, description ->
                viewModel.reportComment(
                    songId = songId,
                    comment = comment,
                    reason = reason,
                    description = description
                )
            },
            onHideComment = { comment ->
                viewModel.hideComment(songId, comment)
            },
            showMessage = ::showToast
        )
    }

    private fun setupRecyclerView() {
        adapter = CommentAdapter(
            currentUserId = activeUserId,
            onLikeClick = { comment ->
                viewModel.toggleCommentLike(songId, comment)
            },
            onMoreClick = { comment, _ ->
                dialogController.showOptions(comment)
            },
            onTimelineClick = { comment ->
                seekToCommentTimeline(comment)
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
            adapter.updateCurrentUserId(viewModel.getCurrentUserId())
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
    private fun seekToCommentTimeline(comment: Comment) {
        val positionMs = comment.timelinePositionMs.coerceAtLeast(0L)
        val song = currentSong

        if (song == null) {
            showToast(getString(R.string.invalid_song))
            return
        }

        val playingSongId = PlayerManager.currentSong.value?.id.orEmpty()

        if (playingSongId == songId) {
            PlayerManager.seekTo(positionMs)
            showToast(
                getString(
                    R.string.seek_to_comment_timeline,
                    formatTimelinePosition(positionMs)
                )
            )
            return
        }

        PlaybackLauncher.openPlayer(
            fragment = this,
            song = song,
            playlist = listOf(song)
        )

        viewLifecycleOwner.lifecycleScope.launch {
            delay(500)
            PlayerManager.seekTo(positionMs)

            showToast(
                getString(
                    R.string.seek_to_comment_timeline,
                    formatTimelinePosition(positionMs)
                )
            )
        }
    }

    private fun formatTimelinePosition(positionMs: Long): String {
        val safePosition = positionMs.coerceAtLeast(0L)

        val totalSeconds = safePosition / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return String.format(
            java.util.Locale.getDefault(),
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
    }

    override fun onResume() {
        super.onResume()

        (requireActivity() as? MainActivity)?.updateMainChromeVisibility()

        val newUserId = viewModel.getCurrentUserId()

        if (newUserId != activeUserId) {
            activeUserId = newUserId

            adapter.updateCurrentUserId(activeUserId)
            adapter.setData(emptyList())

            viewModel.loadComments(songId)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        binding.rvComments.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
