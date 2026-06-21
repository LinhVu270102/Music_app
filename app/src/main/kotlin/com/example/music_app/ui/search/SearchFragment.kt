package com.example.music_app.ui.search

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.data.local.SearchHistoryStore
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.SearchResultBundle
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.User
import com.example.music_app.databinding.FragmentSearchBinding
import com.example.music_app.main.MainActivity
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.library.PlaylistDetailFragment
import com.example.music_app.ui.player.PlaybackLauncher
import com.example.music_app.ui.profile.ApiArtistProfileFragment
import com.example.music_app.ui.profile.ProfileFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchFragment : Fragment(R.layout.fragment_search) {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels()

    private val searchHistoryStore by lazy {
        SearchHistoryStore(requireContext())
    }

    private lateinit var searchAdapter: SearchAdapter

    private var searchResults = SearchResultBundle()
    private var currentSearchSongs: List<Song> = emptyList()
    private var currentTab = SearchTab.ALL

    private var searchJob: Job? = null
    private var isRestoringLatestSearch = false
    private var isApplyingRecentQuery = false

    enum class SearchTab {
        ALL,
        TRACKS,
        PROFILES,
        PLAYLISTS
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentSearchBinding.bind(view)

        setupRecyclerView()
        setupSearchBox()
        setupTabs()
        observeViewModel()

        selectTab(SearchTab.ALL)
        viewModel.loadSongs()

        restoreLatestSearchInSession()
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter(
            onTrackClick = { song ->
                PlaybackLauncher.openPlayer(
                    fragment = this,
                    song = song,
                    playlist = currentSearchSongs
                )
            },
            onProfileClick = { user ->
                openProfileResult(user)
            },
            onApiArtistProfileClick = { profile ->
                openApiArtistProfileResult(profile)
            },
            onPlaylistClick = { playlist ->
                openPlaylistResult(playlist)
            },
            onRecentQueryClick = { query ->
                applyRecentQuery(query)
            }
        )

        binding.rvSearchSongs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }
    }

    private fun setupSearchBox() {
        binding.btnCancel.isVisible = false
        binding.tabContainer.isVisible = false
        binding.tvSearchSectionTitle.text = getString(R.string.recently_searched)

        showRecentSearches()

        binding.edtSearch.setOnFocusChangeListener { _, hasFocus ->
            val mainActivity = activity as? MainActivity

            if (hasFocus) {
                mainActivity?.setFooterVisible(false)
                mainActivity?.setMiniPlayerVisible(false)
            } else {
                mainActivity?.setFooterVisible(true)
                mainActivity?.setMiniPlayerVisible(true)
            }
        }

        binding.edtSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val keyword = binding.edtSearch.text.toString().trim()

                searchJob?.cancel()

                if (keyword.isNotBlank()) {
                    submitSearch(keyword)
                }

                binding.edtSearch.clearFocus()
                hideKeyboard()
                true
            } else {
                false
            }
        }

        binding.edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                val keyword = s.toString().trim()

                if (isRestoringLatestSearch) return
                if (isApplyingRecentQuery) return

                searchJob?.cancel()

                binding.btnCancel.isVisible = keyword.isNotEmpty()
                binding.tabContainer.isVisible = keyword.isNotEmpty()

                if (keyword.isBlank()) {
                    sessionLatestQuery = ""

                    binding.tvSearchSectionTitle.text =
                        getString(R.string.recently_searched)

                    clearSearchUi(clearViewModel = true)
                    showRecentSearches()
                    return
                }

                binding.tvSearchSectionTitle.text = getTitleByTab(currentTab)

                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(500)
                    submitSearch(keyword)
                }
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.btnCancel.setOnClickListener {
            searchJob?.cancel()

            sessionLatestQuery = ""

            binding.edtSearch.text.clear()
            binding.edtSearch.clearFocus()
            hideKeyboard()

            binding.btnCancel.isVisible = false
            binding.tabContainer.isVisible = false
            binding.tvSearchSectionTitle.text = getString(R.string.recently_searched)

            clearSearchUi(clearViewModel = true)
            showRecentSearches()
        }
    }

    private fun setupTabs() {
        binding.tabAll.setOnClickListener {
            selectTab(SearchTab.ALL)
        }

        binding.tabTracks.setOnClickListener {
            selectTab(SearchTab.TRACKS)
        }

        binding.tabProfiles.setOnClickListener {
            selectTab(SearchTab.PROFILES)
        }

        binding.tabPlaylists.setOnClickListener {
            selectTab(SearchTab.PLAYLISTS)
        }
    }

    private fun observeViewModel() {
        viewModel.searchResults.observe(viewLifecycleOwner) { result ->
            searchResults = result

            val keyword = binding.edtSearch.text.toString().trim()
            filterResultsByCurrentTab(keyword)
        }

        viewModel.playSongEvent.observe(viewLifecycleOwner) { song ->
            song?.let {
                PlaybackLauncher.openPlayer(
                    fragment = this,
                    song = it,
                    playlist = currentSearchSongs.ifEmpty {
                        searchResults.tracks
                    }
                )

                viewModel.donePlaySong()
            }
        }

        viewModel.errorMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                viewModel.clearErrorMessage()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.edtSearch.isEnabled = !isLoading
            binding.btnCancel.isEnabled = !isLoading
        }
    }

    private fun restoreLatestSearchInSession() {
        val latestQuery = sessionLatestQuery

        if (latestQuery.isBlank()) {
            binding.btnCancel.isVisible = false
            binding.tabContainer.isVisible = false
            binding.tvSearchSectionTitle.text = getString(R.string.recently_searched)
            showRecentSearches()
            return
        }

        isRestoringLatestSearch = true

        binding.edtSearch.setText(latestQuery)
        binding.edtSearch.setSelection(latestQuery.length)

        binding.btnCancel.isVisible = true
        binding.tabContainer.isVisible = true
        binding.tvSearchSectionTitle.text = getTitleByTab(currentTab)

        isRestoringLatestSearch = false

        viewModel.searchTracks(latestQuery)
    }

    private fun submitSearch(keyword: String) {
        val query = keyword.trim()

        if (query.isBlank()) return

        searchHistoryStore.saveQuery(query)
        sessionLatestQuery = query

        viewModel.searchTracks(query)
    }

    private fun applyRecentQuery(query: String) {
        isApplyingRecentQuery = true

        binding.edtSearch.setText(query)
        binding.edtSearch.setSelection(query.length)

        binding.btnCancel.isVisible = true
        binding.tabContainer.isVisible = true
        binding.tvSearchSectionTitle.text = getTitleByTab(currentTab)

        isApplyingRecentQuery = false

        submitSearch(query)

        binding.edtSearch.clearFocus()
        hideKeyboard()
    }

    private fun showRecentSearches() {
        val recentQueries = searchHistoryStore.getRecentQueries()

        currentSearchSongs = emptyList()
        PlayerManager.setFallbackSongs(emptyList())

        val items = recentQueries.map { query ->
            SearchResultItem.RecentQuery(query)
        }

        searchAdapter.setData(items)
    }

    private fun selectTab(tab: SearchTab) {
        currentTab = tab
        resetTabStyle()

        when (tab) {
            SearchTab.ALL -> {
                binding.tabAll.setBackgroundResource(R.drawable.bg_search_tab_selected)
                binding.tabAll.setTextColor(Color.BLACK)
            }

            SearchTab.TRACKS -> {
                binding.tabTracks.setBackgroundResource(R.drawable.bg_search_tab_selected)
                binding.tabTracks.setTextColor(Color.BLACK)
            }

            SearchTab.PROFILES -> {
                binding.tabProfiles.setBackgroundResource(R.drawable.bg_search_tab_selected)
                binding.tabProfiles.setTextColor(Color.BLACK)
            }

            SearchTab.PLAYLISTS -> {
                binding.tabPlaylists.setBackgroundResource(R.drawable.bg_search_tab_selected)
                binding.tabPlaylists.setTextColor(Color.BLACK)
            }
        }

        val keyword = binding.edtSearch.text.toString().trim()

        binding.tvSearchSectionTitle.text =
            if (keyword.isNotEmpty()) {
                getTitleByTab(tab)
            } else {
                getString(R.string.recently_searched)
            }

        filterResultsByCurrentTab(keyword)
    }

    private fun resetTabStyle() {
        val tabs = listOf(
            binding.tabAll,
            binding.tabTracks,
            binding.tabProfiles,
            binding.tabPlaylists
        )

        tabs.forEach { tab ->
            tab.setBackgroundResource(R.drawable.bg_search_tab_normal)
            tab.setTextColor(Color.WHITE)
        }
    }

    private fun getTitleByTab(tab: SearchTab): String {
        return when (tab) {
            SearchTab.ALL -> getString(R.string.all_results)
            SearchTab.TRACKS -> getString(R.string.tracks)
            SearchTab.PROFILES -> getString(R.string.profiles)
            SearchTab.PLAYLISTS -> getString(R.string.playlists)
        }
    }

    private fun filterResultsByCurrentTab(keyword: String) {
        if (keyword.isBlank()) {
            showRecentSearches()
            return
        }

        val items = when (currentTab) {
            SearchTab.ALL -> {
                currentSearchSongs = searchResults.tracks
                buildAllResultItems(searchResults)
            }

            SearchTab.TRACKS -> {
                currentSearchSongs = searchResults.tracks

                searchResults.tracks.map { song ->
                    SearchResultItem.Track(song)
                }
            }

            SearchTab.PROFILES -> {
                currentSearchSongs = emptyList()

                val firebaseProfiles = searchResults.profiles.map { user ->
                    SearchResultItem.Profile(user)
                }

                val apiProfiles = buildApiArtistProfiles(searchResults.tracks)

                firebaseProfiles + apiProfiles
            }

            SearchTab.PLAYLISTS -> {
                currentSearchSongs = emptyList()

                searchResults.playlists.map { playlist ->
                    SearchResultItem.PlaylistItem(playlist)
                }
            }
        }

        searchAdapter.setData(items)
        PlayerManager.setFallbackSongs(currentSearchSongs)
    }

    private fun buildAllResultItems(
        result: SearchResultBundle
    ): List<SearchResultItem> {
        val items = mutableListOf<SearchResultItem>()

        val apiProfiles = buildApiArtistProfiles(result.tracks)

        val allProfiles = result.profiles.map { user ->
            SearchResultItem.Profile(user)
        } + apiProfiles

        if (result.tracks.isNotEmpty()) {
            items.add(
                SearchResultItem.Header(
                    titleResId = R.string.tracks,
                    count = result.tracks.size
                )
            )

            items.addAll(
                result.tracks.map { song ->
                    SearchResultItem.Track(song)
                }
            )
        }

        if (allProfiles.isNotEmpty()) {
            items.add(
                SearchResultItem.Header(
                    titleResId = R.string.profiles,
                    count = allProfiles.size
                )
            )

            items.addAll(allProfiles)
        }

        if (result.playlists.isNotEmpty()) {
            items.add(
                SearchResultItem.Header(
                    titleResId = R.string.playlists,
                    count = result.playlists.size
                )
            )

            items.addAll(
                result.playlists.map { playlist ->
                    SearchResultItem.PlaylistItem(playlist)
                }
            )
        }

        return items
    }

    private fun buildApiArtistProfiles(
        tracks: List<Song>
    ): List<SearchResultItem.ApiArtistProfile> {
        return tracks
            .filter { song ->
                song.artist.isNotBlank()
            }
            .groupBy { song ->
                song.artist.trim().lowercase()
            }
            .map { (_, songsByArtist) ->
                val firstSong = songsByArtist.first()

                SearchResultItem.ApiArtistProfile(
                    artistName = firstSong.artist,
                    source = firstSong.source.ifBlank {
                        getSongSource(firstSong)
                    },
                    avatarUrl = firstSong.coverUrl,
                    trackCount = songsByArtist.size
                )
            }
            .sortedBy { profile ->
                profile.artistName.lowercase()
            }
    }

    private fun getSongSource(song: Song): String {
        return if (
            song.id.startsWith("soundcloud_", ignoreCase = true) ||
            song.uploaderId.startsWith("soundcloud", ignoreCase = true)
        ) {
            SOURCE_SOUNDCLOUD
        } else {
            SOURCE_ORANGE_MUSIC
        }
    }

    private fun openProfileResult(user: User) {
        if (user.uid.isBlank()) {
            showToast(getString(R.string.target_user_not_found))
            return
        }

        hideKeyboard()
        binding.edtSearch.clearFocus()

        parentFragmentManager.commit {
            replace(
                R.id.fragmentContainer,
                ProfileFragment.newInstance(user.uid)
            )
            addToBackStack(null)
        }
    }

    private fun openApiArtistProfileResult(
        profile: SearchResultItem.ApiArtistProfile
    ) {
        hideKeyboard()
        binding.edtSearch.clearFocus()

        parentFragmentManager.commit {
            replace(
                R.id.fragmentContainer,
                ApiArtistProfileFragment.newInstance(
                    artistName = profile.artistName,
                    source = profile.source,
                    coverUrl = profile.avatarUrl
                )
            )
            addToBackStack(null)
        }
    }

    private fun openPlaylistResult(playlist: Playlist) {
        if (playlist.id.isBlank() || playlist.ownerId.isBlank()) {
            showToast(getString(R.string.playlist_not_found))
            return
        }

        hideKeyboard()
        binding.edtSearch.clearFocus()

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

    private fun clearSearchUi(clearViewModel: Boolean) {
        searchResults = SearchResultBundle()
        currentSearchSongs = emptyList()
        searchAdapter.setData(emptyList())
        PlayerManager.setFallbackSongs(emptyList())

        if (clearViewModel) {
            viewModel.clearSearchResult()
        }
    }

    private fun hideKeyboard() {
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        imm.hideSoftInputFromWindow(binding.edtSearch.windowToken, 0)
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        searchJob?.cancel()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val SOURCE_SOUNDCLOUD = "soundcloud"
        private const val SOURCE_ORANGE_MUSIC = "orange_music"

        private var sessionLatestQuery: String = ""
    }
}