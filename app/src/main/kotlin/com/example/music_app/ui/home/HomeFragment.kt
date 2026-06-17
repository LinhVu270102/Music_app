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
import androidx.recyclerview.widget.RecyclerView
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.FragmentHomeBinding
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.library.YourLikesFragment
import com.example.music_app.ui.player.PlaybackLauncher
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

    private val homeRowCount = 2

    private val genreQueryPairs by lazy {
        listOf(
            binding.chipHipHop to "hip hop rap",
            binding.chipSoundCloud to "soundcloud music",
            binding.chipRnB to "rnb",
            binding.chipPop to "pop music",
            binding.chipElectronic to "electronic music",
            binding.chipChill to "chill lofi"
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

        selectGenreChip(binding.chipHipHop)
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

        binding.rvRelatedTracks.apply {
            layoutManager = GridLayoutManager(
                requireContext(),
                homeRowCount,
                RecyclerView.HORIZONTAL,
                false
            )
            adapter = relatedAdapter
            isNestedScrollingEnabled = false
            setHasFixedSize(true)
        }

        binding.rvMoreLike.apply {
            layoutManager = GridLayoutManager(
                requireContext(),
                homeRowCount,
                RecyclerView.HORIZONTAL,
                false
            )
            adapter = moreLikeAdapter
            isNestedScrollingEnabled = false
            setHasFixedSize(true)
        }

        binding.rvHotForYou.apply {
            layoutManager = GridLayoutManager(
                requireContext(),
                homeRowCount,
                RecyclerView.HORIZONTAL,
                false
            )
            adapter = hotForYouAdapter
            isNestedScrollingEnabled = false
            setHasFixedSize(true)
        }

        binding.rvTrendingByGenre.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = trendingAdapter
            isNestedScrollingEnabled = false
            setHasFixedSize(true)
        }
    }

    private fun setupGenreChips() {
        genreQueryPairs.forEach { pair ->
            val chip = pair.first
            val query = pair.second

            chip.setOnClickListener {
                selectGenreChip(chip)
                viewModel.loadTrendingByGenre(query)
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
        viewModel.relatedTracks.observe(viewLifecycleOwner) { songs ->
            relatedAdapter.setData(songs)
        }

        viewModel.moreLike.observe(viewLifecycleOwner) { songs ->
            moreLikeAdapter.setData(songs)
        }

        viewModel.hotForYou.observe(viewLifecycleOwner) { songs ->
            hotForYouAdapter.setData(songs)
        }

        viewModel.trendingByGenre.observe(viewLifecycleOwner) { songs ->
            trendingAdapter.setData(songs)
        }

        viewModel.playSongEvent.observe(viewLifecycleOwner) { song ->
            song?.let {
                PlayerManager.play(it)
                viewModel.saveRecentlyPlayed(it)

                parentFragmentManager.commit {
                    replace(R.id.fragmentContainer, PlayerFragment.newInstance(it.id))
                    addToBackStack(null)
                }

                viewModel.donePlaySong()
            }
        }

        viewModel.errorMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                viewModel.clearErrorMessage()
            }
        }
    }

    private fun openPlayer(song: Song) {
        PlaybackLauncher.openPlayer(
            fragment = this,
            song = song
        )
    }

    private fun selectGenreChip(selectedChip: TextView) {
        genreQueryPairs.forEach { pair ->
            val chip = pair.first

            chip.setBackgroundResource(R.drawable.bg_genre_chip)
            chip.setTextColor(requireContext().getColor(R.color.white))
        }

        selectedChip.setBackgroundResource(R.drawable.bg_genre_chip_selected)
        selectedChip.setTextColor(requireContext().getColor(R.color.whiter))
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}