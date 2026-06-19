package com.example.music_app.ui.library

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.music_app.R
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.FragmentLibraryBinding
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.albums.AlbumsFragment
import com.example.music_app.ui.player.PlaybackLauncher
import com.example.music_app.ui.setting.SettingFragment
import com.example.music_app.ui.song.SongAdapter

class LibraryFragment : Fragment() {

    companion object {
        private const val TAG = "LibraryFragment"
    }

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LibraryViewModel by viewModels()

    private var currentRecentlySongs: List<Song> = emptyList()

    private lateinit var recentlyAdapter: SongAdapter
    private lateinit var playlistAdapter: LibraryPlaylistAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "onViewCreated called")

        setupRecyclerViews()
        setupListeners()
        observeViewModel()

        viewModel.loadLibraryData()
    }

    private fun setupRecyclerViews() {
        recentlyAdapter = SongAdapter(
            onItemClick = { song ->
                openPlayer(song)
            }
        )

        playlistAdapter = LibraryPlaylistAdapter(
            onItemClick = { playlist ->
                openPlaylistDetail(playlist)
            }
        )

        binding.albumHorizontalList.apply {
            layoutManager = GridLayoutManager(
                requireContext(),
                2,
                RecyclerView.HORIZONTAL,
                false
            )
            adapter = recentlyAdapter
            isNestedScrollingEnabled = false
            setHasFixedSize(true)
        }

        binding.albumHorizontalList1.apply {
            layoutManager = GridLayoutManager(
                requireContext(),
                2,
                RecyclerView.HORIZONTAL,
                false
            )
            adapter = playlistAdapter
            isNestedScrollingEnabled = false
            setHasFixedSize(true)
        }
    }

    private fun setupListeners() {
        binding.btnSetting.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, SettingFragment())
                addToBackStack(null)
            }
        }

        binding.btnYourLikes.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, YourLikesFragment())
                addToBackStack(null)
            }
        }

        binding.btnPlaylists.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, PlaylistsFragment())
                addToBackStack(null)
            }
        }

        binding.btnAlbums.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, AlbumsFragment())
                addToBackStack(null)
            }
        }

        binding.btnFollowing.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, FollowingFragment())
                addToBackStack(null)
            }
        }

        binding.btnYourUpload.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, YourUploadFragment())
                addToBackStack(null)
            }
        }

        binding.btnSeeAllLib1.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, YourLikesFragment())
                addToBackStack(null)
            }
        }

        binding.btnSeeAllLib2.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragmentContainer, PlaylistsFragment())
                addToBackStack(null)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.recentlyPlayed.observe(viewLifecycleOwner) { songs ->
            currentRecentlySongs = songs
            recentlyAdapter.setData(songs)
            PlayerManager.setFallbackSongs(songs)
        }

        viewModel.playlists.observe(viewLifecycleOwner) { playlists ->
            playlistAdapter.setData(playlists)
        }

        viewModel.errorMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                viewModel.clearErrorMessage()
            }
        }
    }

    private fun openPlayer(song: Song) {
        PlaybackLauncher.openPlayer(
            fragment = this,
            song = song,
            playlist = currentRecentlySongs
        )
    }

    private fun openPlaylistDetail(playlist: Playlist) {
        parentFragmentManager.commit {
            replace(
                R.id.fragmentContainer,
                PlaylistDetailFragment.newInstance(
                    playlistId = playlist.id,
                    playlistName = playlist.name
                )
            )
            addToBackStack(null)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}