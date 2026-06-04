package com.example.music_app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.FragmentProfileBinding
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.player.PlayerFragment
import com.example.music_app.ui.song.SongAdapter

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    private lateinit var adapter: SongAdapter

    private var currentSongs: List<Song> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?
        , savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupRecyclerView()
        observeViewModel()
        viewModel.loadProfile()
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter(
            onItemClick = { song ->
                playSong(song)
            }
        )

        binding.profileMusicList.layoutManager = LinearLayoutManager(requireContext())
        binding.profileMusicList.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.user.observe(viewLifecycleOwner) { user ->
            if (user == null) {
                binding.profileName.text = getString(R.string.guest)
                binding.profileEmail.text = getString(R.string.not_logged_in)

                Glide.with(this)
                    .load(R.drawable.music_orange)
                    .into(binding.profileAvatar)

                return@observe
            }

            binding.profileName.text =
                if (user.displayName.isNotBlank()) {
                    user.displayName
                } else {
                    getString(R.string.no_name)
                }

            binding.profileEmail.text = user.email

            Glide.with(this)
                .load(user.avatarUrl.ifBlank { R.drawable.music_orange })
                .placeholder(R.drawable.music_orange)
                .error(R.drawable.music_orange)
                .into(binding.profileAvatar)
        }

        viewModel.mySongs.observe(viewLifecycleOwner) { songs ->
            currentSongs = songs

            binding.songCount.text =
                getString(R.string.song_count_format, songs.size)

            binding.playlistCount.text =
                getString(R.string.playlist_count_format, 0)

            adapter.setData(songs)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility =
                if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                Toast.makeText(requireContext(), getString(it), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playSong(song: Song) {
        PlayerManager.play(song)

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