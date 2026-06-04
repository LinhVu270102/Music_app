package com.example.music_app.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.FragmentArtistProfileBinding
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.player.PlayerFragment
import com.example.music_app.ui.song.SongAdapter

class ArtistProfileFragment : Fragment(R.layout.fragment_artist_profile) {

    private var _binding: FragmentArtistProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ArtistProfileViewModel by viewModels()

    private lateinit var adapter: SongAdapter
    private var artistId: String = ""

    private var currentSongs: List<Song> = emptyList()

    companion object {
        private const val ARG_USER_ID = "userId"

        fun newInstance(userId: String): ArtistProfileFragment {
            return ArtistProfileFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_USER_ID, userId)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentArtistProfileBinding.bind(view)

        artistId = arguments?.getString(ARG_USER_ID).orEmpty()

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadArtistProfile(artistId)
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter(
            onItemClick = { song ->
                playSong(song)
            }
        )

        binding.rvArtistSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvArtistSongs.adapter = adapter
        binding.rvArtistSongs.isNestedScrollingEnabled = false
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
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
        }

        viewModel.songs.observe(viewLifecycleOwner) { songs ->
            currentSongs = songs

            adapter.setData(songs)

            binding.tvEmpty.isVisible = songs.isEmpty()
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                Toast.makeText(requireContext(), getString(it), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playSong(song: Song) {
        if (currentSongs.isNotEmpty()) {
            PlayerManager.setPlaylist(currentSongs)
        }

        PlayerManager.play(song)

        parentFragmentManager.commit {
            replace(R.id.fragmentContainer, PlayerFragment.newInstance(song.id))
            addToBackStack(null)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}