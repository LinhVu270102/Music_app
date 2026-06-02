package com.example.music_app.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.music_app.R
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

    companion object {
        fun newInstance(userId: String): ArtistProfileFragment {
            return ArtistProfileFragment().apply {
                arguments = Bundle().apply {
                    putString("userId", userId)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentArtistProfileBinding.bind(view)

        artistId = arguments?.getString("userId").orEmpty()

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadArtistProfile(artistId)
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter { song ->
            PlayerManager.play(song)

            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, PlayerFragment.newInstance(song.id))
                addToBackStack(null)
            }
        }

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

            binding.tvHeaderTitle.text = user.displayName.ifBlank { "Artist" }
            binding.tvArtistName.text = user.displayName.ifBlank { user.email }
            binding.tvArtistUsername.text =
                if (user.username.isNotBlank()) "@${user.username}" else user.email
            binding.tvArtistBio.text = user.bio.ifBlank { "No bio" }

            Glide.with(this)
                .load(user.avatarUrl)
                .placeholder(R.drawable.music_orange)
                .circleCrop()
                .into(binding.imgArtistAvatar)
        }

        viewModel.songs.observe(viewLifecycleOwner) { songs ->
            adapter.setData(songs)

            binding.tvEmpty.visibility =
                if (songs.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}