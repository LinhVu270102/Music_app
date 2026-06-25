package com.example.music_app.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.databinding.FragmentAdminCommentModerationBinding

class AdminCommentModerationFragment : Fragment(R.layout.fragment_admin_comment_moderation) {

    private var _binding: FragmentAdminCommentModerationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminCommentModerationViewModel by viewModels()

    private lateinit var adapter: AdminCommentModerationAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentAdminCommentModerationBinding.bind(view)

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadReportedComments()
    }

    private fun setupRecyclerView() {
        adapter = AdminCommentModerationAdapter(
            onHideComment = { comment ->
                viewModel.hideComment(comment)
            }
        )

        binding.recyclerReportedComments.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerReportedComments.adapter = adapter
    }

    private fun setupListeners() {
        binding.swipeRefreshAdminComments.setOnRefreshListener {
            viewModel.loadReportedComments()
        }

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnReload.setOnClickListener {
            viewModel.loadReportedComments()
        }
    }

    private fun observeViewModel() {
        viewModel.comments.observe(viewLifecycleOwner) { comments ->
            adapter.submitList(comments)

            binding.txtEmptyComments.visibility =
                if (comments.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressLoading.visibility =
                if (isLoading) View.VISIBLE else View.GONE
            binding.swipeRefreshAdminComments.isRefreshing = isLoading
        }

        viewModel.messageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                Toast.makeText(requireContext(), getString(it), Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
                binding.swipeRefreshAdminComments.isRefreshing = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
