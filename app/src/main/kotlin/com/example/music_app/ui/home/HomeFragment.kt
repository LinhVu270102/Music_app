package com.example.music_app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.FragmentHomeBinding
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.library.YourLikesFragment
import com.example.music_app.ui.player.PlayerFragment
import com.example.music_app.ui.song.SongAdapter

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    private lateinit var relatedAdapter: SongAdapter
    private lateinit var moreLikeAdapter: SongAdapter
    private lateinit var hotForYouAdapter: SongAdapter
    private lateinit var trendingAdapter: SongAdapter

    private var allSongs: List<Song> = emptyList()

    private val genreChips by lazy {
        listOf(
            binding.chipHipHop,
            binding.chipSoundCloud,
            binding.chipRnB,
            binding.chipPop,
            binding.chipElectronic,
            binding.chipChill
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupRecyclerViews()
        setupGenreChips()
        setupListeners()
        observeViewModel()

        viewModel.loadHomeData()
    }

    private fun setupRecyclerViews() {
        relatedAdapter = SongAdapter(
            onItemClick = { song ->
                openPlayer(song)
            }
        )

        moreLikeAdapter = SongAdapter(
            onItemClick = { song ->
                openPlayer(song)
            }
        )

        hotForYouAdapter = SongAdapter(
            onItemClick = { song ->
                openPlayer(song)
            }
        )

        trendingAdapter = SongAdapter(
            onItemClick = { song ->
                openPlayer(song)
            }
        )

        binding.rvRelatedTracks.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvRelatedTracks.adapter = relatedAdapter
        binding.rvRelatedTracks.isNestedScrollingEnabled = false

        binding.rvMoreLike.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvMoreLike.adapter = moreLikeAdapter
        binding.rvMoreLike.isNestedScrollingEnabled = false

        binding.rvHotForYou.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvHotForYou.adapter = hotForYouAdapter
        binding.rvHotForYou.isNestedScrollingEnabled = false

        binding.rvTrendingByGenre.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvTrendingByGenre.adapter = trendingAdapter
        binding.rvTrendingByGenre.isNestedScrollingEnabled = false
    }

    private fun setupGenreChips() {
        genreChips.forEach { chip ->
            chip.setOnClickListener {
                selectGenreChip(chip)
                filterTrendingByGenre(chip.text.toString())
            }
        }
    }

    private fun setupListeners() {
        binding.sectionYourLikes.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, YourLikesFragment())
                addToBackStack(null)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.playlist.observe(viewLifecycleOwner) { songs ->
            allSongs = songs

            PlayerManager.setPlaylist(songs)

            relatedAdapter.setData(songs.take(4))
            moreLikeAdapter.setData(songs)
            hotForYouAdapter.setData(songs.sortedByDescending { it.plays })

            filterTrendingByGenre(binding.chipHipHop.text.toString())
        }

        viewModel.errorMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                viewModel.clearErrorMessage()
            }
        }
    }

    private fun openPlayer(song: Song) {
        PlayerManager.play(song)
        viewModel.saveRecentlyPlayed(song)

        parentFragmentManager.commit {
            replace(R.id.fragmentContainer, PlayerFragment.newInstance(song.id))
            addToBackStack(null)
        }
    }

    private fun selectGenreChip(selectedChip: TextView) {
        genreChips.forEach { chip ->
            chip.setBackgroundResource(R.drawable.bg_genre_chip)
            chip.setTextColor(requireContext().getColor(R.color.white))
        }

        selectedChip.setBackgroundResource(R.drawable.bg_genre_chip_selected)
        selectedChip.setTextColor(requireContext().getColor(R.color.black))
    }

    private fun filterTrendingByGenre(genre: String) {
        val filteredSongs = allSongs.filter { song ->
            song.genre.equals(genre, ignoreCase = true)
        }

        if (filteredSongs.isNotEmpty()) {
            trendingAdapter.setData(filteredSongs)
        } else {
            trendingAdapter.setData(allSongs)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}