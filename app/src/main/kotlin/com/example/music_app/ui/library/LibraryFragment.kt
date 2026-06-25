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
import com.example.music_app.ui.following.FollowingFragment
import com.example.music_app.ui.player.PlaybackLauncher
import com.example.music_app.ui.playlists.PlaylistDetailFragment
import com.example.music_app.ui.playlists.PlaylistsFragment
import com.example.music_app.ui.setting.SettingFragment
import com.example.music_app.ui.song.SongAdapter
import com.example.music_app.ui.yourupload.YourUploadFragment
import com.example.music_app.ui.yourlikes.YourLikesFragment

class LibraryFragment : Fragment() {

    companion object {
        private const val TAG = "LibraryFragment"
        private const val RECENTLY_PLAYED_LIMIT = 20
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

        binding.recyclerRecentlyPlayed.apply {
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

        binding.recyclerSavedPlaylists.apply {
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

    }

    private fun observeViewModel() {
        viewModel.recentlyPlayed.observe(viewLifecycleOwner) { songs ->
            showRecentlyPlayedSongs(songs)
        }

        PlayerManager.currentSong.observe(viewLifecycleOwner) { song ->
            if (song == null) return@observe
            viewModel.recordJustPlayed(song)
        }

        viewModel.playlists.observe(viewLifecycleOwner) { playlists ->
            playlistAdapter.setData(playlists)
            binding.tvEmptySavedPlaylists.visibility =
                if (playlists.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.errorMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                viewModel.clearErrorMessage()
            }
        }
    }

    private fun showRecentlyPlayedSongs(songs: List<Song>) {
        currentRecentlySongs = songs
        recentlyAdapter.setData(songs)
        binding.tvEmptyRecentlyPlayed.visibility =
            if (songs.isEmpty()) View.VISIBLE else View.GONE
        PlayerManager.setFallbackSongs(songs)
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
                    playlistName = playlist.name,
                    ownerId = playlist.ownerId,
                    coverUrl = playlist.coverUrl
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

    override fun onResume() {
        super.onResume()
        viewModel.loadLibraryData()
    }
}
