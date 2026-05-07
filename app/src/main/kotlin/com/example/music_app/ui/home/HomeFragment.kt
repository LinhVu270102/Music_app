package com.example.music_app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.databinding.FragmentHomeBinding
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.player.PlayerFragment
import com.example.music_app.ui.song.SongAdapter

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var adapter: SongAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupRecyclerView()
        initObservers()
        viewModel.loadPlaylist()
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter { song ->
            PlayerManager.play(song)

            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, PlayerFragment.newInstance(song.id))
                addToBackStack(null)
            }
        }

        binding.albumGrid.layoutManager = LinearLayoutManager(requireContext())
        binding.albumGrid.adapter = adapter
        binding.albumGrid.isNestedScrollingEnabled = false
    }

    private fun initObservers() {
        viewModel.playlist.observe(viewLifecycleOwner) { songs ->
            adapter.setData(songs)
            PlayerManager.setPlaylist(songs)
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