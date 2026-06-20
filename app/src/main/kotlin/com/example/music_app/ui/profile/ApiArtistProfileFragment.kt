package com.example.music_app.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.remote.soundcloud.model.SoundCloudArtistProfileDto
import com.example.music_app.databinding.FragmentApiArtistProfileBinding
import com.example.music_app.ui.player.PlaybackLauncher
import com.example.music_app.ui.song.SongAdapter

class ApiArtistProfileFragment : Fragment(R.layout.fragment_api_artist_profile) {

    private var _binding: FragmentApiArtistProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ApiArtistProfileViewModel by viewModels()

    private lateinit var adapter: SongAdapter
    private var currentSongs: List<Song> = emptyList()

    private var artistName: String = ""
    private var source: String = ""
    private var coverUrl: String = ""

    companion object {
        private const val ARG_ARTIST_NAME = "artistName"
        private const val ARG_SOURCE = "source"
        private const val ARG_COVER_URL = "coverUrl"

        fun newInstance(
            artistName: String,
            source: String = "soundcloud",
            coverUrl: String = ""
        ): ApiArtistProfileFragment {
            return ApiArtistProfileFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ARTIST_NAME, artistName)
                    putString(ARG_SOURCE, source)
                    putString(ARG_COVER_URL, coverUrl)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentApiArtistProfileBinding.bind(view)

        artistName = arguments?.getString(ARG_ARTIST_NAME).orEmpty()
        source = arguments?.getString(ARG_SOURCE).orEmpty()
        coverUrl = arguments?.getString(ARG_COVER_URL).orEmpty()

        setupRecyclerView()
        setupListeners()
        observeViewModel()
        bindFallbackHeader()

        viewModel.loadArtistProfile(artistName)
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter(
            onItemClick = { song ->
                openPlayer(song)
            }
        )

        binding.rvArtistSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvArtistSongs.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnPlayArtistSongs.setOnClickListener {
            val firstSong = currentSongs.firstOrNull()

            if (firstSong == null) {
                showToast(getString(R.string.no_artist_songs))
            } else {
                openPlayer(firstSong)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.profile.observe(viewLifecycleOwner) { profile ->
            bindProfileFromServer(profile)
        }

        viewModel.songs.observe(viewLifecycleOwner) { songs ->
            currentSongs = songs
            adapter.setData(songs)

            binding.tvArtistSongCount.text =
                getString(R.string.artist_profile_songs_count, songs.size)

            binding.tvEmptyArtistSongs.isVisible = songs.isEmpty()
            binding.rvArtistSongs.isVisible = songs.isNotEmpty()

            val firstCover = songs.firstOrNull()?.coverUrl.orEmpty()

            if (coverUrl.isBlank() && firstCover.isNotBlank()) {
                Glide.with(this)
                    .load(firstCover)
                    .placeholder(R.drawable.music_orange)
                    .error(R.drawable.music_orange)
                    .circleCrop()
                    .into(binding.imgArtistCover)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressArtist.isVisible = isLoading
        }

        viewModel.errorMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                viewModel.clearErrorMessage()
            }
        }
    }

    private fun bindFallbackHeader() {
        binding.tvArtistName.text =
            artistName.ifBlank {
                getString(R.string.unknown_artist)
            }

        binding.tvArtistSource.text =
            getString(
                R.string.api_artist_source_format,
                getSourceLabel(source)
            )

        binding.tvArtistSongCount.text =
            getString(R.string.artist_profile_songs_count_placeholder)

        Glide.with(this)
            .load(coverUrl.ifBlank { R.drawable.music_orange })
            .placeholder(R.drawable.music_orange)
            .error(R.drawable.music_orange)
            .circleCrop()
            .into(binding.imgArtistCover)
    }

    private fun bindProfileFromServer(profile: SoundCloudArtistProfileDto) {
        binding.tvArtistName.text =
            profile.displayName.ifBlank {
                profile.artistName.ifBlank {
                    artistName.ifBlank {
                        getString(R.string.unknown_artist)
                    }
                }
            }

        binding.tvArtistSource.text =
            getString(
                R.string.api_artist_source_format,
                profile.sourceLabel.ifBlank {
                    getSourceLabel(profile.source)
                }
            )

        binding.tvArtistSongCount.text =
            getString(R.string.artist_profile_songs_count, profile.tracksCount)

        val finalAvatar = profile.avatarUrl.ifBlank {
            profile.bannerUrl.ifBlank {
                coverUrl
            }
        }

        Glide.with(this)
            .load(finalAvatar.ifBlank { R.drawable.music_orange })
            .placeholder(R.drawable.music_orange)
            .error(R.drawable.music_orange)
            .circleCrop()
            .into(binding.imgArtistCover)
    }

    private fun openPlayer(song: Song) {
        PlaybackLauncher.openPlayer(
            fragment = this,
            song = song,
            playlist = currentSongs.ifEmpty { listOf(song) }
        )
    }

    private fun getSourceLabel(source: String): String {
        return if (source.equals("soundcloud", ignoreCase = true)) {
            getString(R.string.soundcloud_source)
        } else {
            getString(R.string.orange_music_source)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}