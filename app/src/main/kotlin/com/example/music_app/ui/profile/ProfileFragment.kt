package com.example.music_app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.music_app.R
import com.example.music_app.databinding.FragmentProfileBinding
import com.example.music_app.ui.player.PlayerFragment
import com.example.music_app.ui.song.SongAdapter

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SongAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupProfile()
        setupRecyclerView()
    }

    private fun setupProfile() {
        binding.profileName.text = "Hoài"
        binding.profileEmail.text = "hoai@example.com"
        binding.songCount.text = "Songs: 0"
        binding.playlistCount.text = "Playlists: 0"

        Glide.with(this)
            .load("https://via.placeholder.com/150")
            .placeholder(R.drawable.music_orange)
            .into(binding.profileAvatar)
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter { song ->
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, PlayerFragment.newInstance(song.id))
                .addToBackStack(null)
                .commit()
        }

        binding.profileMusicList.layoutManager = LinearLayoutManager(requireContext())
        binding.profileMusicList.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}