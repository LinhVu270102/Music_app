package com.example.music_app.ui.albums

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.music_app.R
import com.example.music_app.databinding.FragmentAlbumsBinding

class AlbumsFragment : Fragment(R.layout.fragment_albums) {

    private var _binding: FragmentAlbumsBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentAlbumsBinding.bind(view)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}