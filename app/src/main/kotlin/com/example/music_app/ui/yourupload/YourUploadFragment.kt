package com.example.music_app.ui.library

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.FragmentYourUploadBinding
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.player.PlayerFragment
import com.example.music_app.ui.song.SongAdapter

class YourUploadFragment : Fragment(R.layout.fragment_your_upload) {

    private var _binding: FragmentYourUploadBinding? = null
    private val binding get() = _binding!!

    private val viewModel: YourUploadViewModel by viewModels()

    private lateinit var adapter: SongAdapter
    private var currentSongs: List<Song> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentYourUploadBinding.bind(view)

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadMyUploadedSongs()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadMyUploadedSongs()
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter { song ->
            if (currentSongs.isNotEmpty()) {
                PlayerManager.setPlaylist(currentSongs)
            }

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
        viewModel.songs.observe(viewLifecycleOwner) { songs ->
            currentSongs = songs
            adapter.setData(songs)

            binding.tvEmpty.visibility =
                if (songs.isEmpty()) View.VISIBLE else View.GONE
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