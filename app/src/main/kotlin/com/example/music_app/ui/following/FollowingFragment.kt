package com.example.music_app.ui.library

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.databinding.FragmentFollowingBinding

class FollowingFragment : Fragment(R.layout.fragment_following) {

    private var _binding: FragmentFollowingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FollowingViewModel by viewModels()

    private lateinit var adapter: FollowingAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentFollowingBinding.bind(view)

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadFollowingUsers()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadFollowingUsers()
    }

    private fun setupRecyclerView() {
        adapter = FollowingAdapter { user ->
            Toast.makeText(
                requireContext(),
                "Artist: ${user.displayName.ifBlank { user.email }}",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun observeViewModel() {
        viewModel.followingUsers.observe(viewLifecycleOwner) { users ->
            adapter.setData(users)

            binding.tvEmpty.visibility =
                if (users.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}