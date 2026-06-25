package com.example.music_app.ui.yourlikes

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.FragmentYourLikesBinding
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.player.PlaybackLauncher
import com.example.music_app.ui.player.PlayerFragment
import com.example.music_app.ui.song.SongAdapter

class YourLikesFragment : Fragment(R.layout.fragment_your_likes) {

    private var _binding: FragmentYourLikesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: YourLikesViewModel by viewModels()

    private lateinit var adapter: SongAdapter

    private var currentSongs: List<Song> = emptyList()

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
        adapter = SongAdapter(
            onItemClick = { song ->
                openPlayer(song)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        binding.swipeRefreshYourLikes.setOnRefreshListener {
            viewModel.loadLikedSongs()
        }

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun observeViewModel() {
        viewModel.likedSongs.observe(viewLifecycleOwner) { songs ->
            currentSongs = songs
            adapter.setData(songs)
            binding.swipeRefreshYourLikes.isRefreshing = false
        }

        viewModel.errorMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                viewModel.clearErrorMessage()
                binding.swipeRefreshYourLikes.isRefreshing = false
            }
        }
    }

    private fun openPlayer(song: Song) {
        PlaybackLauncher.openPlayer(
            fragment = this,
            song = song,
            playlist = currentSongs
        )
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
