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
import com.example.music_app.databinding.FragmentArtistProfileBinding
import com.example.music_app.player.state.PlayerInteractionState
import com.example.music_app.ui.player.PlaybackLauncher
import com.example.music_app.ui.song.SongAdapter

class ArtistProfileFragment : Fragment(R.layout.fragment_artist_profile) {

    private var _binding: FragmentArtistProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ArtistProfileViewModel by viewModels()

    private lateinit var adapter: SongAdapter
    private var artistId: String = ""
    private var artistName: String = ""

    private var currentSongs: List<Song> = emptyList()

    companion object {
        private const val ARG_USER_ID = "userId"
        private const val ARG_ARTIST_NAME = "artistName"

        fun newInstance(userId: String): ArtistProfileFragment {
            return ArtistProfileFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_USER_ID, userId)
                }
            }
        }

        fun newArtistInstance(artistName: String): ArtistProfileFragment {
            return ArtistProfileFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ARTIST_NAME, artistName)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentArtistProfileBinding.bind(view)

        artistId = arguments?.getString(ARG_USER_ID).orEmpty()
        artistName = arguments?.getString(ARG_ARTIST_NAME).orEmpty()

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadArtistProfile(
            userId = artistId,
            artistName = artistName
        )
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter(
            onItemClick = { song ->
                playSong(song)
            },
            onLikeClick = viewModel::toggleSongLike,
            isSongLiked = { song ->
                viewModel.songLikeStates.value?.get(song.id) == true
            },
            useFullWidth = true
        )

        binding.rvArtistSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvArtistSongs.adapter = adapter
        binding.rvArtistSongs.isNestedScrollingEnabled = false
    }

    private fun setupListeners() {
        binding.swipeRefreshArtistProfile.setOnRefreshListener {
            viewModel.loadArtistProfile(
                userId = artistId,
                artistName = artistName
            )
        }

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.btnFollowArtist.setOnClickListener {
            viewModel.toggleFollow()
        }
    }

    private fun observeViewModel() {
        viewModel.artist.observe(viewLifecycleOwner) { user ->
            if (user == null) return@observe

            binding.tvHeaderTitle.text =
                user.displayName.ifBlank { getString(R.string.artist) }

            binding.tvArtistName.text =
                user.displayName.ifBlank { user.email }

            binding.tvArtistUsername.text =
                if (user.username.isNotBlank()) {
                    "@${user.username}"
                } else {
                    user.email
                }

            binding.tvArtistBio.text =
                user.bio.ifBlank { getString(R.string.no_bio) }

            Glide.with(this)
                .load(user.avatarUrl.ifBlank { R.drawable.music_orange })
                .placeholder(R.drawable.music_orange)
                .error(R.drawable.music_orange)
                .circleCrop()
                .into(binding.imgArtistAvatar)
            binding.swipeRefreshArtistProfile.isRefreshing = false
        }

        viewModel.songs.observe(viewLifecycleOwner) { songs ->
            currentSongs = songs

            adapter.setData(songs)
            adapter.setLikedSongIds(viewModel.likedSongIds)

            binding.tvEmpty.isVisible = songs.isEmpty()
            binding.swipeRefreshArtistProfile.isRefreshing = false
        }

        viewModel.songLikeStates.observe(viewLifecycleOwner) {
            adapter.setLikedSongIds(viewModel.likedSongIds)
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                Toast.makeText(requireContext(), getString(it), Toast.LENGTH_SHORT).show()
                viewModel.clearErrorMessage()
                binding.swipeRefreshArtistProfile.isRefreshing = false
            }
        }

        viewModel.canFollow.observe(viewLifecycleOwner) { canFollow ->
            binding.btnFollowArtist.isVisible = canFollow
        }

        viewModel.isFollowing.observe(viewLifecycleOwner) { isFollowing ->
            binding.btnFollowArtist.setImageResource(
                if (isFollowing) R.drawable.ic_followed else R.drawable.ic_follow
            )
            binding.btnFollowArtist.contentDescription = getString(
                if (isFollowing) R.string.following else R.string.follow
            )
            binding.btnFollowArtist.alpha = if (isFollowing) 1f else 0.55f
        }

        PlayerInteractionState.artistFollowUpdates.observe(viewLifecycleOwner) { state ->
            viewModel.applySharedFollowState(state)
        }

        PlayerInteractionState.songLikeUpdates.observe(viewLifecycleOwner) { state ->
            viewModel.applySharedSongLikeState(state)
        }
    }

    private fun playSong(song: Song) {
        PlaybackLauncher.openPlayer(
            fragment = this,
            song = song,
            playlist = currentSongs
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
