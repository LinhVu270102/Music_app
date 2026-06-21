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
import com.example.music_app.ui.playlists.PlaylistDetailFragment
import com.example.music_app.ui.player.PlaybackLauncher
import com.example.music_app.ui.profile.ApiArtistProfileFragment
import com.example.music_app.ui.profile.ProfileFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.music_app.data.remote.soundcloud.SoundCloudRetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

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
    private var profileCountJob: Job? = null
    private val artistTrackCountCache = mutableMapOf<String, Int>()

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

                    clearSearchUi()
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

            clearSearchUi()
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
            val keyword = binding.edtSearch.text.toString().trim()

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

        val normalizedKeyword = normalizeSearchText(keyword)

        val items = when (currentTab) {
            SearchTab.ALL -> {
                val filteredTracks = searchResults.tracks
                    .filter { song ->
                        isSongMatchKeyword(song, normalizedKeyword)
                    }

                currentSearchSongs = filteredTracks

                buildAllResultItems(
                    result = searchResults,
                    keyword = keyword
                )
            }

            SearchTab.TRACKS -> {
                val filteredTracks = searchResults.tracks
                    .filter { song ->
                        isSongMatchKeyword(song, normalizedKeyword)
                    }

                currentSearchSongs = filteredTracks

                filteredTracks.map { song ->
                    SearchResultItem.Track(song)
                }
            }

            SearchTab.PROFILES -> {
                currentSearchSongs = emptyList()

                val firebaseProfiles = searchResults.profiles
                    .filter { user ->
                        isUserMatchKeyword(user, normalizedKeyword)
                    }
                    .map { user ->
                        SearchResultItem.Profile(user)
                    }

                val apiProfiles = buildApiArtistProfiles(
                    tracks = searchResults.tracks,
                    keyword = keyword
                )

                firebaseProfiles + apiProfiles
            }

            SearchTab.PLAYLISTS -> {
                currentSearchSongs = emptyList()

                searchResults.playlists
                    .filter { playlist ->
                        isPlaylistMatchKeyword(playlist, normalizedKeyword)
                    }
                    .map { playlist ->
                        SearchResultItem.PlaylistItem(playlist)
                    }
            }
        }

        searchAdapter.setData(items)
        PlayerManager.setFallbackSongs(currentSearchSongs)

        updateApiArtistProfileTrackCounts(
            items = items,
            keywordSnapshot = keyword,
            tabSnapshot = currentTab
        )
    }

    private fun buildAllResultItems(
        result: SearchResultBundle,
        keyword: String
    ): List<SearchResultItem> {
        val normalizedKeyword = normalizeSearchText(keyword)

        val trackItems = result.tracks
            .filter { song ->
                isSongMatchKeyword(song, normalizedKeyword)
            }
            .map { song ->
                SearchResultItem.Track(song)
            }

        val firebaseProfileItems = result.profiles
            .filter { user ->
                isUserMatchKeyword(user, normalizedKeyword)
            }
            .map { user ->
                SearchResultItem.Profile(user)
            }

        val apiProfileItems = buildApiArtistProfiles(
            tracks = result.tracks,
            keyword = keyword
        )

        val playlistItems = result.playlists
            .filter { playlist ->
                isPlaylistMatchKeyword(playlist, normalizedKeyword)
            }
            .map { playlist ->
                SearchResultItem.PlaylistItem(playlist)
            }

        return (trackItems + firebaseProfileItems + apiProfileItems + playlistItems)
            .sortedByDescending { item ->
                getSearchItemScore(item, normalizedKeyword)
            }
    }

    private fun buildApiArtistProfiles(
        tracks: List<Song>,
        keyword: String
    ): List<SearchResultItem.ApiArtistProfile> {
        val normalizedKeyword = normalizeSearchText(keyword)

        return tracks
            .asSequence()
            .filter { song ->
                getSongSource(song).equals(SOURCE_SOUNDCLOUD, ignoreCase = true)
            }
            .filter { song ->
                song.artist.isNotBlank()
            }
            .filter { song ->
                val normalizedArtist = normalizeSearchText(song.artist)

                normalizedKeyword.isNotBlank() &&
                        (
                                normalizedArtist.contains(normalizedKeyword) ||
                                        normalizedKeyword.contains(normalizedArtist)
                                )
            }
            .groupBy { song ->
                normalizeSearchText(song.artist)
            }
            .map { entry ->
                val artistTracks = entry.value
                    .distinctBy { song -> song.id }

                val representativeSong = artistTracks
                    .maxByOrNull { song -> song.plays }
                    ?: artistTracks.first()

                SearchResultItem.ApiArtistProfile(
                    artistName = representativeSong.artist.trim(),
                    source = SOURCE_SOUNDCLOUD,
                    avatarUrl = representativeSong.coverUrl,
                    trackCount = artistTracks.size
                )
            }
            .sortedWith(
                compareByDescending<SearchResultItem.ApiArtistProfile> { profile ->
                    profile.trackCount
                }.thenBy { profile ->
                    profile.artistName.lowercase()
                }
            )
    }
    private fun isSongMatchKeyword(
        song: Song,
        normalizedKeyword: String
    ): Boolean {
        if (normalizedKeyword.isBlank()) return false

        val title = normalizeSearchText(song.title)
        val artist = normalizeSearchText(song.artist)
        val genre = normalizeSearchText(song.genre)

        return title.contains(normalizedKeyword) ||
                artist.contains(normalizedKeyword) ||
                genre.contains(normalizedKeyword)
    }
    private fun getSearchItemScore(
        item: SearchResultItem,
        normalizedKeyword: String
    ): Int {
        return when (item) {
            is SearchResultItem.Track -> {
                val title = normalizeSearchText(item.song.title)
                val artist = normalizeSearchText(item.song.artist)

                when {
                    title == normalizedKeyword -> 100
                    artist == normalizedKeyword -> 95
                    title.startsWith(normalizedKeyword) -> 90
                    artist.startsWith(normalizedKeyword) -> 85
                    title.contains(normalizedKeyword) -> 80
                    artist.contains(normalizedKeyword) -> 75
                    else -> 0
                }
            }

            is SearchResultItem.ApiArtistProfile -> {
                val artistName = normalizeSearchText(item.artistName)

                when {
                    artistName == normalizedKeyword -> 100
                    artistName.startsWith(normalizedKeyword) -> 90
                    artistName.contains(normalizedKeyword) -> 80
                    else -> 0
                }
            }

            is SearchResultItem.Profile -> {
                val displayName = normalizeSearchText(item.user.displayName)
                val username = normalizeSearchText(item.user.username)

                when {
                    displayName == normalizedKeyword -> 100
                    username == normalizedKeyword -> 95
                    displayName.startsWith(normalizedKeyword) -> 90
                    username.startsWith(normalizedKeyword) -> 85
                    displayName.contains(normalizedKeyword) -> 80
                    username.contains(normalizedKeyword) -> 75
                    else -> 0
                }
            }

            is SearchResultItem.PlaylistItem -> {
                val name = normalizeSearchText(item.playlist.name)

                when {
                    name == normalizedKeyword -> 100
                    name.startsWith(normalizedKeyword) -> 90
                    name.contains(normalizedKeyword) -> 80
                    else -> 0
                }
            }

            else -> 0
        }
    }


    private fun isUserMatchKeyword(
        user: User,
        normalizedKeyword: String
    ): Boolean {
        if (normalizedKeyword.isBlank()) return false

        val displayName = normalizeSearchText(user.displayName)
        val username = normalizeSearchText(user.username)
        val email = normalizeSearchText(user.email)

        return displayName.contains(normalizedKeyword) ||
                username.contains(normalizedKeyword) ||
                email.contains(normalizedKeyword)
    }

    private fun isPlaylistMatchKeyword(
        playlist: Playlist,
        normalizedKeyword: String
    ): Boolean {
        if (normalizedKeyword.isBlank()) return false

        val name = normalizeSearchText(playlist.name)
        val description = normalizeSearchText(playlist.description)

        return name.contains(normalizedKeyword) ||
                description.contains(normalizedKeyword)
    }
    private fun normalizeSearchText(value: String): String {
        return java.text.Normalizer
            .normalize(value, java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .trim()
            .lowercase()
    }
    private fun updateApiArtistProfileTrackCounts(
        items: List<SearchResultItem>,
        keywordSnapshot: String,
        tabSnapshot: SearchTab
    ) {
        val apiProfiles = items.filterIsInstance<SearchResultItem.ApiArtistProfile>()

        if (apiProfiles.isEmpty()) return

        profileCountJob?.cancel()

        profileCountJob = viewLifecycleOwner.lifecycleScope.launch {
            val updatedProfiles = mutableMapOf<String, SearchResultItem.ApiArtistProfile>()

            apiProfiles.forEach { profile ->
                val key = profile.artistName.trim().lowercase()

                val apiTrackCount = artistTrackCountCache[key]
                    ?: fetchApiArtistTrackCount(profile.artistName).also { count ->
                        if (count > 0) {
                            artistTrackCountCache[key] = count
                        }
                    }

                updatedProfiles[key] = profile.copy(
                    trackCount = maxOf(profile.trackCount, apiTrackCount)
                )
            }

            if (_binding == null) return@launch

            val currentKeyword = binding.edtSearch.text.toString().trim()

            if (!currentKeyword.equals(keywordSnapshot, ignoreCase = true)) {
                return@launch
            }

            if (currentTab != tabSnapshot) {
                return@launch
            }

            val updatedItems = items.map { item ->
                if (item is SearchResultItem.ApiArtistProfile) {
                    val key = item.artistName.trim().lowercase()
                    updatedProfiles[key] ?: item
                } else {
                    item
                }
            }

            searchAdapter.setData(updatedItems)
        }
    }

    private suspend fun fetchApiArtistTrackCount(
        artistName: String
    ): Int {
        return withContext(Dispatchers.IO) {
            try {
                val response = SoundCloudRetrofitClient.api.getArtistTracks(
                    artist = artistName,
                    limit = ARTIST_TRACK_COUNT_LIMIT
                )

                if (!response.isSuccessful) {
                    return@withContext 0
                }

                val body = response.body()?.string().orEmpty()

                if (body.isBlank()) {
                    return@withContext 0
                }

                val json = JSONObject(body)
                val results = json.optJSONArray("results")

                results?.length() ?: 0
            } catch (_: Exception) {
                0
            }
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

    private fun clearSearchUi() {
        profileCountJob?.cancel()
        artistTrackCountCache.clear()

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
        profileCountJob?.cancel()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val SOURCE_SOUNDCLOUD = "soundcloud"
        private const val SOURCE_ORANGE_MUSIC = "orange_music"

        private var sessionLatestQuery: String = ""
        private const val ARTIST_TRACK_COUNT_LIMIT = 20
    }
}