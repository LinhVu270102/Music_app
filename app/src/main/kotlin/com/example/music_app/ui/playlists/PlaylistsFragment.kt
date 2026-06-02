package com.example.music_app.ui.playlists

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.music_app.R
import com.example.music_app.databinding.FragmentPlaylistsBinding

class PlaylistsFragment : Fragment(R.layout.fragment_playlists) {

    private var _binding: FragmentPlaylistsBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentPlaylistsBinding.bind(view)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}