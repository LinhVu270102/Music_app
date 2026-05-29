package com.example.music_app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.FragmentHomeBinding
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.player.PlayerFragment
import com.example.music_app.ui.song.SongAdapter
import android.graphics.Color
import android.graphics.Typeface
import android.widget.TextView

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var allSongs: List<Song> = emptyList()

    private val viewModel: HomeViewModel by viewModels()

    private lateinit var relatedAdapter: SongAdapter
    private lateinit var moreLikeAdapter: SongAdapter
    private lateinit var hotForYouAdapter: SongAdapter
    private lateinit var trendingAdapter: SongAdapter

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
        observeViewModel()
        viewModel.loadHomeData()
    }

    private fun setupRecyclerViews() {
        relatedAdapter = SongAdapter { song ->
            playSong(song)
        }

        moreLikeAdapter = SongAdapter { song ->
            playSong(song)
        }

        hotForYouAdapter = SongAdapter { song ->
            playSong(song)
        }

        trendingAdapter = SongAdapter { song ->
            playSong(song)
        }

        // Related tracks: 4 bài, dạng 2 hàng ngang giống SoundCloud
        binding.rvRelatedTracks.layoutManager =
            GridLayoutManager(requireContext(), 2, RecyclerView.HORIZONTAL, false)
        binding.rvRelatedTracks.adapter = relatedAdapter
        binding.rvRelatedTracks.isNestedScrollingEnabled = false

        // More of what you like: danh sách ngang
        binding.rvMoreLike.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvMoreLike.adapter = moreLikeAdapter
        binding.rvMoreLike.isNestedScrollingEnabled = false

        // Hot For You: danh sách ngang
        binding.rvHotForYou.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvHotForYou.adapter = hotForYouAdapter
        binding.rvHotForYou.isNestedScrollingEnabled = false

        // Trending by genre: danh sách dọc
        binding.rvTrendingByGenre.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.rvTrendingByGenre.adapter = trendingAdapter
        binding.rvTrendingByGenre.isNestedScrollingEnabled = false
    }

    private fun setupGenreChips() {
        binding.chipHipHop.setOnClickListener {
            selectGenreChip(binding.chipHipHop, "Hip Hop & Rap")
        }

        binding.chipSoundCloud.setOnClickListener {
            selectGenreChip(binding.chipSoundCloud, "SoundCloud")
        }

        binding.chipRnB.setOnClickListener {
            selectGenreChip(binding.chipRnB, "R&B")
        }

        binding.chipPop.setOnClickListener {
            selectGenreChip(binding.chipPop, "Pop")
        }

        binding.chipElectronic.setOnClickListener {
            selectGenreChip(binding.chipElectronic, "Electronic")
        }

        binding.chipChill.setOnClickListener {
            selectGenreChip(binding.chipChill, "Chill")
        }
    }

    private fun selectGenreChip(selectedChip: TextView, genre: String) {
        val chips = listOf(
            binding.chipHipHop,
            binding.chipSoundCloud,
            binding.chipRnB,
            binding.chipPop,
            binding.chipElectronic,
            binding.chipChill
        )

        chips.forEach { chip ->
            chip.setBackgroundResource(R.drawable.bg_genre_chip)
            chip.setTextColor(Color.parseColor("#DDDDDD"))
            chip.setTypeface(null, Typeface.NORMAL)
        }

        selectedChip.setBackgroundResource(R.drawable.bg_genre_chip_selected)
        selectedChip.setTextColor(Color.parseColor("#DFA8FF"))
        selectedChip.setTypeface(null, Typeface.BOLD)

        filterSongsByGenre(genre)
    }

    private fun filterSongsByGenre(genre: String) {
        // Hiện tại model Song của bạn chưa có field genre,
        // nên tạm thời chỉ đổi dữ liệu demo theo từng chip.
        val filteredSongs = when (genre) {
            "Hip Hop & Rap" -> allSongs.take(6)
            "SoundCloud" -> allSongs.drop(1).take(6)
            "R&B" -> allSongs.drop(2).take(6)
            "Pop" -> allSongs.drop(3).take(6)
            "Electronic" -> allSongs.sortedByDescending { it.plays }.take(6)
            "Chill" -> allSongs.shuffled().take(6)
            else -> allSongs.take(6)
        }

        trendingAdapter.setData(filteredSongs)
    }

    private fun observeViewModel() {
        viewModel.playlist.observe(viewLifecycleOwner) { songs ->
            allSongs = songs

            PlayerManager.setPlaylist(songs)

            relatedAdapter.setData(songs.take(4))
            moreLikeAdapter.setData(songs.drop(1).take(8))
            hotForYouAdapter.setData(songs.sortedByDescending { it.plays }.take(8))
            trendingAdapter.setData(songs.take(6))
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playSong(song: Song) {
        PlayerManager.play(song)
        viewModel.saveRecentlyPlayed(song)

        parentFragmentManager.commit {
            replace(R.id.fragmentContainer, PlayerFragment.newInstance(song.id))
            addToBackStack(null)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}