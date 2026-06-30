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
import com.example.music_app.player.state.PlayerInteractionState
import com.example.music_app.ui.notification.NotificationFragment
import com.example.music_app.ui.player.PlaybackLauncher
import com.example.music_app.ui.song.SongAdapter
import com.example.music_app.ui.yourupload.UploadMusicFragment
import com.example.music_app.ui.yourlikes.YourLikesFragment

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    private lateinit var relatedAdapter: SongAdapter
    private lateinit var moreLikeAdapter: SongAdapter
    private lateinit var hotForYouAdapter: SongAdapter
    private lateinit var trendingAdapter: SongAdapter

    private var relatedSongs: List<Song> = emptyList()
    private var moreLikeSongs: List<Song> = emptyList()
    private var hotForYouSongs: List<Song> = emptyList()
    private var trendingSongs: List<Song> = emptyList()

    private val homeRowCount = 2

    private val genreQueryPairs by lazy {
        listOf(
            binding.chipHipHop to "hip hop rap",
            binding.chipRock to "rock",
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
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshHomeDataByUser()
        }

        setupNavigationButtons()
        setupRecyclerViews()
        setupGenreChips()
        setupListeners()
        observeViewModel()
        observeLikeChanges()

        selectGenreChip(binding.chipHipHop)
        viewModel.loadHomeDataFast()
    }

    private fun setupNavigationButtons() {
        binding.btnNotification.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, NotificationFragment())
                addToBackStack(null)
            }
        }

        binding.btnUpload.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, UploadMusicFragment())
                addToBackStack(null)
            }
        }
    }

    private fun setupRecyclerViews() {
        relatedAdapter = SongAdapter(
            onItemClick = { song ->
                openPlayer(song, relatedSongs)
            }
        )

        moreLikeAdapter = SongAdapter(
            onItemClick = { song ->
                openPlayer(song, moreLikeSongs)
            }
        )

        hotForYouAdapter = SongAdapter(
            onItemClick = { song ->
                openPlayer(song, hotForYouSongs)
            }
        )

        trendingAdapter = SongAdapter(
            onItemClick = { song ->
                openPlayer(song, trendingSongs)
            }
        )

        setupHorizontalSongList(binding.rvRelatedTracks, relatedAdapter)
        setupHorizontalSongList(binding.rvMoreLike, moreLikeAdapter)
        setupHorizontalSongList(binding.rvHotForYou, hotForYouAdapter)
        setupTrendingSongList()
    }

    private fun setupGenreChips() {
        genreQueryPairs.forEach { pair ->
            val chip = pair.first
            val query = pair.second

            chip.setOnClickListener {
                selectGenreChip(chip)
                viewModel.loadTrendingByGenreFast(query)
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
            relatedSongs = songs
            relatedAdapter.setData(songs)
            updateFallbackSongs()
        }

        viewModel.moreLike.observe(viewLifecycleOwner) { songs ->
            moreLikeSongs = songs
            moreLikeAdapter.setData(songs)
            updateFallbackSongs()
        }

        viewModel.hotForYou.observe(viewLifecycleOwner) { songs ->
            hotForYouSongs = songs
            hotForYouAdapter.setData(songs)
            updateFallbackSongs()
        }

        viewModel.trendingByGenre.observe(viewLifecycleOwner) { songs ->
            trendingSongs = songs
            trendingAdapter.setData(songs)
            updateFallbackSongs()
        }
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefreshLayout.isRefreshing = isLoading
        }

        viewModel.errorMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId ?: return@observe
            showToast(getString(messageResId))
            viewModel.clearErrorMessage()
        }
    }

    private fun observeLikeChanges() {
        PlayerInteractionState.songLikeUpdates.observe(viewLifecycleOwner) { state ->
            if (state.changedByUser) {
                viewModel.refreshHomeDataAfterLikeChanged()
            }
        }
    }

    private fun updateFallbackSongs() {
        val songs = listOf(
            relatedSongs,
            moreLikeSongs,
            hotForYouSongs,
            trendingSongs
        )
            .flatten()
            .distinctBy { song -> song.id }

        if (songs.isNotEmpty()) {
            PlayerManager.setFallbackSongs(songs)
        }
    }

    private fun openPlayer(
        song: Song,
        playlist: List<Song>
    ) {
        PlaybackLauncher.openPlayer(
            fragment = this,
            song = song,
            playlist = playlist
        )
    }

    private fun setupHorizontalSongList(
        recyclerView: RecyclerView,
        songAdapter: SongAdapter
    ) {
        recyclerView.apply {
            layoutManager = GridLayoutManager(
                requireContext(),
                homeRowCount,
                RecyclerView.HORIZONTAL,
                false
            )
            adapter = songAdapter
            isNestedScrollingEnabled = false
            setHasFixedSize(true)
            itemAnimator = null
        }
    }

    private fun setupTrendingSongList() {
        binding.rvTrendingByGenre.apply {
            layoutManager = GridLayoutManager(requireContext(), homeRowCount)
            adapter = trendingAdapter
            isNestedScrollingEnabled = false
            setHasFixedSize(true)
            itemAnimator = null
        }
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
