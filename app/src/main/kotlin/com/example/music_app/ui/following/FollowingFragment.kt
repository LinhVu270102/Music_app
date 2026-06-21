package com.example.music_app.ui.library

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.databinding.FragmentFollowingBinding
import com.example.music_app.ui.profile.ArtistProfileFragment

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
            if (user.uid.isBlank()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.invalid_user),
                    Toast.LENGTH_SHORT
                ).show()
                return@FollowingAdapter
            }

            parentFragmentManager.commit {
                replace(
                    R.id.fragmentContainer,
                    ArtistProfileFragment.newInstance(user.uid)
                )
                addToBackStack(null)
            }
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
            binding.tvEmpty.isVisible = users.isEmpty()
            binding.recyclerView.isVisible = users.isNotEmpty()
        }

        viewModel.errorMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                Toast.makeText(requireContext(), getString(it), Toast.LENGTH_SHORT).show()
                viewModel.clearErrorMessage()
            }
        }
    }

    override fun onDestroyView() {
        binding.recyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }
}