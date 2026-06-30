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
import com.example.music_app.data.model.enums.SearchTab
import com.example.music_app.databinding.FragmentSearchBinding
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.playlists.PlaylistDetailFragment
import com.example.music_app.ui.player.PlaybackLauncher
import com.example.music_app.ui.profile.ArtistProfileFragment
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
    private val searchResultComposer = SearchResultComposer()

    private var searchResults = SearchResultBundle()
    private var currentSearchSongs: List<Song> = emptyList()
    private var currentTab = SearchTab.ALL

    private var searchJob: Job? = null
    private var isRestoringLatestSearch = false
    private var isApplyingRecentQuery = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentSearchBinding.bind(view)

        setupRecyclerView()
        setupSearchBox()
        setupTabs()
        setupRefresh()
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
            itemAnimator = null
            setHasFixedSize(true)
        }
    }

    private fun setupSearchBox() {
        showRecentSearchMode()
        showRecentSearches()

        binding.edtSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitEditorSearch()
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
                handleSearchTextChanged(s.toString())
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.btnCancel.setOnClickListener {
            clearSearchInputAndShowRecent()
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

    private fun setupRefresh() {
        binding.swipeRefreshSearch.setOnRefreshListener {
            refreshSearchResults()
        }
    }

    private fun observeViewModel() {
        viewModel.searchResults.observe(viewLifecycleOwner) { result ->
            val keyword = currentKeyword()

            if (
                keyword.isNotBlank() &&
                result.query.isNotBlank() &&
                !result.query.equals(keyword, ignoreCase = true)
            ) {
                return@observe
            }

            searchResults = result
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
                binding.swipeRefreshSearch.isRefreshing = false
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefreshSearch.isRefreshing = isLoading
        }

    }

    private fun submitEditorSearch(): Boolean {
        val keyword = currentKeyword()

        searchJob?.cancel()

        if (keyword.isNotBlank()) {
            submitSearch(keyword)
        }

        binding.edtSearch.clearFocus()
        hideKeyboard()
        return true
    }

    private fun handleSearchTextChanged(text: String) {
        val keyword = text.trim()

        if (isRestoringLatestSearch) return
        if (isApplyingRecentQuery) return

        searchJob?.cancel()

        if (keyword.isBlank()) {
            sessionLatestQuery = ""
            showRecentSearchMode()
            clearSearchUi()
            showRecentSearches()
            return
        }

        showActiveSearchMode()

        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            submitSearch(keyword)
        }
    }

    private fun refreshSearchResults() {
        val keyword = currentKeyword()

        if (keyword.isBlank()) {
            viewModel.loadSongs()
            showRecentSearches()
            binding.swipeRefreshSearch.isRefreshing = false
            return
        }

        submitSearch(keyword)
    }

    private fun restoreLatestSearchInSession() {
        val latestQuery = sessionLatestQuery

        if (latestQuery.isBlank()) {
            showRecentSearchMode()
            showRecentSearches()
            return
        }

        isRestoringLatestSearch = true

        setSearchBoxText(latestQuery)
        showActiveSearchMode()

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

        setSearchBoxText(query)
        showActiveSearchMode()

        isApplyingRecentQuery = false

        submitSearch(query)

        binding.edtSearch.clearFocus()
        hideKeyboard()
    }

    private fun clearSearchInputAndShowRecent() {
        searchJob?.cancel()
        sessionLatestQuery = ""

        binding.edtSearch.text.clear()
        binding.edtSearch.clearFocus()
        hideKeyboard()

        showRecentSearchMode()
        clearSearchUi()
        showRecentSearches()
    }

    private fun setSearchBoxText(query: String) {
        binding.edtSearch.setText(query)
        binding.edtSearch.setSelection(query.length)
    }

    private fun showRecentSearchMode() {
        binding.btnCancel.isVisible = false
        binding.tabContainer.isVisible = false
        binding.tvSearchSectionTitle.text = getString(R.string.recently_searched)
    }

    private fun showActiveSearchMode() {
        binding.btnCancel.isVisible = true
        binding.tabContainer.isVisible = true
        binding.tvSearchSectionTitle.text = getTitleByTab(currentTab)
    }

    private fun currentKeyword(): String {
        return binding.edtSearch.text.toString().trim()
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

        val keyword = currentKeyword()

        binding.tvSearchSectionTitle.text = searchSectionTitle(tab, keyword)

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

    private fun searchSectionTitle(tab: SearchTab, keyword: String): String {
        return if (keyword.isNotEmpty()) {
            getTitleByTab(tab)
        } else {
            getString(R.string.recently_searched)
        }
    }

    private fun filterResultsByCurrentTab(keyword: String) {
        if (keyword.isBlank()) {
            showRecentSearches()
            return
        }

        val presentation = searchResultComposer.compose(
            result = searchResults,
            keyword = keyword,
            tab = currentTab
        )
        val items = presentation.items
        currentSearchSongs = presentation.playableSongs

        searchAdapter.setData(items)
        PlayerManager.setFallbackSongs(currentSearchSongs)
    }

    private fun openProfileResult(user: User) {
        if (user.uid.isBlank()) {
            showToast(getString(R.string.target_user_not_found))
            return
        }

        hideKeyboard()
        binding.edtSearch.clearFocus()

        val artistFragment = if (user.uid.startsWith(ARTIST_PROFILE_PREFIX)) {
            ArtistProfileFragment.newArtistInstance(user.displayName)
        } else {
            ArtistProfileFragment.newInstance(user.uid)
        }

        parentFragmentManager.commit {
            replace(
                R.id.fragmentContainer,
                artistFragment
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

    private fun clearSearchUi() {
        searchResults = SearchResultBundle()
        currentSearchSongs = emptyList()
        searchAdapter.setData(emptyList())
        PlayerManager.setFallbackSongs(emptyList())

        viewModel.clearSearchResult()
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
        private const val ARTIST_PROFILE_PREFIX = "artist:"
        private const val SEARCH_DEBOUNCE_MS = 500L
        private var sessionLatestQuery: String = ""
    }
}
