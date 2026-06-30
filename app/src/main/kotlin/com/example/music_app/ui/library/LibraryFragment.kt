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

        setupHorizontalList(binding.recyclerRecentlyPlayed, recentlyAdapter)
        setupHorizontalList(binding.recyclerSavedPlaylists, playlistAdapter)
    }

    private fun setupListeners() {
        binding.swipeRefreshLibrary.setOnRefreshListener {
            viewModel.loadLibraryData()
        }

        binding.btnSetting.setOnClickListener {
            openFragment(SettingFragment())
        }

        binding.btnYourLikes.setOnClickListener {
            openFragment(YourLikesFragment())
        }

        binding.btnPlaylists.setOnClickListener {
            openFragment(PlaylistsFragment())
        }

        binding.btnFollowing.setOnClickListener {
            openFragment(FollowingFragment())
        }

        binding.btnYourUpload.setOnClickListener {
            openFragment(YourUploadFragment())
        }

    }

    private fun observeViewModel() {
        viewModel.recentlyPlayed.observe(viewLifecycleOwner) { songs ->
            showRecentlyPlayedSongs(songs)
            binding.swipeRefreshLibrary.isRefreshing = false
        }

        PlayerManager.currentSong.observe(viewLifecycleOwner) { song ->
            if (song == null) return@observe
            viewModel.recordJustPlayed(song)
        }

        PlayerManager.playbackContext.observe(viewLifecycleOwner) { context ->
            viewModel.recordJustPlayedPlaylist(context)
        }

        viewModel.playlists.observe(viewLifecycleOwner) { playlists ->
            showRecentlyPlayedPlaylists(playlists)
        }

        viewModel.errorMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                viewModel.clearErrorMessage()
                binding.swipeRefreshLibrary.isRefreshing = false
            }
        }
    }

    private fun showRecentlyPlayedSongs(songs: List<Song>) {
        currentRecentlySongs = songs
        recentlyAdapter.setData(songs)
        binding.tvEmptyRecentlyPlayed.visibility = emptyVisibility(songs)
        PlayerManager.setFallbackSongs(songs)
    }

    private fun showRecentlyPlayedPlaylists(playlists: List<Playlist>) {
        playlistAdapter.setData(playlists)
        binding.tvEmptySavedPlaylists.visibility = emptyVisibility(playlists)
        binding.swipeRefreshLibrary.isRefreshing = false
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

    private fun setupHorizontalList(
        recyclerView: RecyclerView,
        listAdapter: RecyclerView.Adapter<*>
    ) {
        recyclerView.apply {
            layoutManager = GridLayoutManager(
                requireContext(),
                2,
                RecyclerView.HORIZONTAL,
                false
            )
            adapter = listAdapter
            isNestedScrollingEnabled = false
            setHasFixedSize(true)
        }
    }

    private fun openFragment(fragment: Fragment) {
        parentFragmentManager.commit {
            replace(R.id.fragmentContainer, fragment)
            addToBackStack(null)
        }
    }

    private fun emptyVisibility(items: List<*>): Int {
        return if (items.isEmpty()) View.VISIBLE else View.GONE
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
