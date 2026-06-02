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
        fun newInstance(songId: String): CommentFragment {
            return CommentFragment().apply {
                arguments = Bundle().apply {
                    putString("songId", songId)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentCommentBinding.bind(view)

        (requireActivity() as? MainActivity)?.updateMainChromeVisibility()

        songId = arguments?.getString("songId").orEmpty()

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
                Toast.makeText(
                    requireContext(),
                    "Vui lòng nhập bình luận",
                    Toast.LENGTH_SHORT
                ).show()
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

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? MainActivity)?.updateMainChromeVisibility()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}