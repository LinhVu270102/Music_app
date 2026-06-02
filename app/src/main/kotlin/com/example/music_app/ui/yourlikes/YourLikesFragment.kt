package com.example.music_app.ui.library

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.databinding.FragmentYourLikesBinding
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.player.PlayerFragment
import com.example.music_app.ui.song.SongAdapter

class YourLikesFragment : Fragment(R.layout.fragment_your_likes) {

    private var _binding: FragmentYourLikesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: YourLikesViewModel by viewModels()

    private lateinit var adapter: SongAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentYourLikesBinding.bind(view)

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadLikedSongs()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadLikedSongs()
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter { song ->
            PlayerManager.play(song)

            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, PlayerFragment.newInstance(song.id))
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
        viewModel.likedSongs.observe(viewLifecycleOwner) { songs ->
            adapter.setData(songs)
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}