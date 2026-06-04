package com.example.music_app.ui.comment

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.databinding.FragmentCommentBinding
import com.example.music_app.main.MainActivity

class CommentFragment : Fragment(R.layout.fragment_comment) {

    private var _binding: FragmentCommentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CommentViewModel by viewModels()
    private lateinit var adapter: CommentAdapter

    private var songId: String = ""

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

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadComments(songId)
    }

    private fun setupRecyclerView() {
        adapter = CommentAdapter()

        binding.rvComments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvComments.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnSendComment.setOnClickListener {
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
        viewModel.comments.observe(viewLifecycleOwner) { comments ->
            adapter.setData(comments)
        }

        viewModel.errorMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                viewModel.clearErrorMessage()
            }
        }
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