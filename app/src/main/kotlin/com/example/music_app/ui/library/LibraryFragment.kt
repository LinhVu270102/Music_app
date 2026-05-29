package com.example.music_app.ui.library

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
import com.example.music_app.databinding.FragmentLibraryBinding
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.player.PlayerFragment
import com.example.music_app.ui.settings.SettingFragment
import com.example.music_app.ui.song.SongAdapter

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LibraryViewModel by viewModels()

    private lateinit var recentlyAdapter: SongAdapter
    private lateinit var historyAdapter: SongAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupRecyclerViews()
        setupListeners()
        observeViewModel()

        viewModel.loadRecentlyPlayed()
    }

    private fun setupRecyclerViews() {
        recentlyAdapter = SongAdapter { song ->
            PlayerManager.play(song)

            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, PlayerFragment.newInstance(song.id))
                addToBackStack(null)
            }
        }

        historyAdapter = SongAdapter { song ->
            PlayerManager.play(song)

            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, PlayerFragment.newInstance(song.id))
                addToBackStack(null)
            }
        }

        binding.albumHorizontalList.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.albumHorizontalList.adapter = recentlyAdapter
        binding.albumHorizontalList.isNestedScrollingEnabled = false

        binding.albumHorizontalList1.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.albumHorizontalList1.adapter = historyAdapter
        binding.albumHorizontalList1.isNestedScrollingEnabled = false
    }

    private fun setupListeners() {
        binding.btnSetting.setOnClickListener {
            viewModel.onSettingClicked()
        }

        binding.btnYourLikes.setOnClickListener {
            showToast("Bạn chọn Your Likes")
        }

        binding.btnPlaylists.setOnClickListener {
            showToast("Bạn chọn Playlists")
        }

        binding.btnAlbums.setOnClickListener {
            showToast("Bạn chọn Albums")
        }

        binding.btnFollowing.setOnClickListener {
            showToast("Bạn chọn Following")
        }

        binding.btnYourUpload.setOnClickListener {
            showToast("Bạn chọn Your Upload")
        }
    }

    private fun observeViewModel() {
        viewModel.recentlyPlayed.observe(viewLifecycleOwner) { songs ->
            recentlyAdapter.setData(songs)

            // Tạm thời Listening history dùng cùng data với Recently Played.
            // Sau này có thể tách riêng full listening history.
            historyAdapter.setData(songs)
        }

        viewModel.navigateEvent.observe(viewLifecycleOwner) { event ->
            if (event == "setting") {
                parentFragmentManager.commit {
                    replace(R.id.fragmentContainer, SettingFragment())
                    addToBackStack(null)
                }
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                showToast(it)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadRecentlyPlayed()
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}