package com.example.music_app.ui.search

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaRecorder
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import com.example.music_app.ui.search.state.AudioSearchUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SearchFragment : Fragment(R.layout.fragment_search) {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels()
    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startAudioSearchRecording()
            } else {
                showToast(getString(R.string.audio_search_permission_required))
            }
        }

    private val searchHistoryStore by lazy {
        SearchHistoryStore(requireContext())
    }

    private lateinit var searchAdapter: SearchAdapter
    private val searchResultComposer = SearchResultComposer()

    private var searchResults = SearchResultBundle()
    private var currentSearchSongs: List<Song> = emptyList()
    private var currentTab = SearchTab.ALL

    private var searchJob: Job? = null
    private var audioRecordingJob: Job? = null
    private var audioRecorder: MediaRecorder? = null
    private var audioSampleFile: File? = null

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

        binding.btnAudioSearch.setOnClickListener {
            handleAudioSearchClick()
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

        viewModel.audioSearchState.observe(viewLifecycleOwner) { state ->
            renderAudioSearchState(state)
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

    private fun handleAudioSearchClick() {
        hideKeyboard()
        binding.edtSearch.clearFocus()

        if (audioRecorder != null) {
            completeAudioSearchRecording(cancelTimer = true)
            return
        }

        val hasRecordPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasRecordPermission) {
            startAudioSearchRecording()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startAudioSearchRecording() {
        if (audioRecorder != null) return

        val outputFile = File(
            requireContext().cacheDir,
            "$AUDIO_SAMPLE_FILE_PREFIX${System.currentTimeMillis()}.$AUDIO_SAMPLE_FILE_EXTENSION"
        )

        try {
            audioRecorder = createAudioRecorder(outputFile).also { recorder ->
                recorder.prepare()
                recorder.start()
            }
            audioSampleFile = outputFile
            showAudioRecordingMode()

            audioRecordingJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(AUDIO_SAMPLE_DURATION_MS)
                completeAudioSearchRecording(cancelTimer = false)
            }
        } catch (error: Exception) {
            Log.w(TAG, "start audio search recording failed: ${error.message}", error)
            releaseAudioRecorder(stopRecorder = false)
            audioSampleFile = null
            outputFile.delete()
            binding.swipeRefreshSearch.isRefreshing = false
            setAudioSearchButtonRecording(false)
            showToast(getString(R.string.audio_search_recording_failed))
        }
    }

    @Suppress("DEPRECATION")
    private fun createAudioRecorder(outputFile: File): MediaRecorder {
        return MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(AUDIO_SAMPLE_RATE)
            setAudioEncodingBitRate(AUDIO_ENCODING_BIT_RATE)
            setOutputFile(outputFile.absolutePath)
        }
    }

    private fun completeAudioSearchRecording(cancelTimer: Boolean) {
        if (cancelTimer) {
            audioRecordingJob?.cancel()
        }
        audioRecordingJob = null

        val sampleFile = audioSampleFile
        audioSampleFile = null

        val stoppedSuccessfully = releaseAudioRecorder(stopRecorder = true)
        setAudioSearchButtonRecording(false)

        if (!stoppedSuccessfully || sampleFile == null || sampleFile.length() == 0L) {
            sampleFile?.delete()
            binding.swipeRefreshSearch.isRefreshing = false
            showToast(getString(R.string.audio_search_recording_failed))
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val audioBase64 = withContext(Dispatchers.IO) {
                Base64.encodeToString(sampleFile.readBytes(), Base64.NO_WRAP)
            }
            withContext(Dispatchers.IO) {
                sampleFile.delete()
            }

            viewModel.searchByAudioSample(
                audioBase64 = audioBase64,
                fileExtension = AUDIO_SAMPLE_FILE_EXTENSION
            )
        }
    }

    private fun releaseAudioRecorder(stopRecorder: Boolean): Boolean {
        val recorder = audioRecorder ?: return false
        audioRecorder = null

        val stoppedSuccessfully = if (stopRecorder) {
            runCatching {
                recorder.stop()
            }.onFailure { error ->
                Log.w(TAG, "stop audio search recording failed: ${error.message}", error)
            }.isSuccess
        } else {
            false
        }

        runCatching { recorder.reset() }
        runCatching { recorder.release() }

        return stoppedSuccessfully || !stopRecorder
    }

    private fun renderAudioSearchState(state: AudioSearchUiState) {
        when (state) {
            AudioSearchUiState.Idle -> Unit
            AudioSearchUiState.Searching -> {
                binding.btnAudioSearch.isEnabled = false
                binding.tvSearchSectionTitle.text = getString(R.string.audio_search_searching)
                binding.swipeRefreshSearch.isRefreshing = true
            }

            is AudioSearchUiState.Success -> {
                binding.btnAudioSearch.isEnabled = true
                showAudioSearchResults(state.songs)
                viewModel.clearAudioSearchState()
            }

            AudioSearchUiState.NoMatch -> {
                binding.btnAudioSearch.isEnabled = true
                showAudioSearchResults(emptyList())
                showToast(getString(R.string.audio_search_no_match))
                viewModel.clearAudioSearchState()
            }

            is AudioSearchUiState.Error -> {
                binding.btnAudioSearch.isEnabled = true
                binding.swipeRefreshSearch.isRefreshing = false
                showToast(getString(state.messageResId))
                viewModel.clearAudioSearchState()
            }
        }
    }

    private fun showAudioRecordingMode() {
        searchJob?.cancel()
        searchResults = SearchResultBundle()
        currentSearchSongs = emptyList()
        searchAdapter.setData(emptyList())
        PlayerManager.setFallbackSongs(emptyList())

        binding.btnCancel.isVisible = true
        binding.tabContainer.isVisible = false
        binding.tvSearchSectionTitle.text = getString(R.string.audio_search_listening)
        binding.swipeRefreshSearch.isRefreshing = true
        setAudioSearchButtonRecording(true)
    }

    private fun showAudioSearchResults(songs: List<Song>) {
        searchResults = SearchResultBundle(tracks = songs)
        currentSearchSongs = songs

        binding.btnCancel.isVisible = true
        binding.tabContainer.isVisible = false
        binding.tvSearchSectionTitle.text = getString(R.string.audio_search_results)
        binding.swipeRefreshSearch.isRefreshing = false

        searchAdapter.setData(songs.map(SearchResultItem::Track))
        PlayerManager.setFallbackSongs(songs)
    }

    private fun setAudioSearchButtonRecording(isRecording: Boolean) {
        val iconColor = if (isRecording) {
            Color.parseColor(AUDIO_RECORDING_ICON_COLOR)
        } else {
            Color.parseColor(AUDIO_IDLE_ICON_COLOR)
        }

        binding.btnAudioSearch.setColorFilter(iconColor)
        binding.btnAudioSearch.isSelected = isRecording
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
        cancelAudioSearch()
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

    private fun cancelAudioSearch() {
        audioRecordingJob?.cancel()
        audioRecordingJob = null
        releaseAudioRecorder(stopRecorder = false)
        audioSampleFile?.delete()
        audioSampleFile = null

        binding.btnAudioSearch.isEnabled = true
        binding.swipeRefreshSearch.isRefreshing = false
        setAudioSearchButtonRecording(false)
        viewModel.cancelAudioSearch()
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
        audioRecordingJob?.cancel()
        audioRecordingJob = null
        releaseAudioRecorder(stopRecorder = true)
        audioSampleFile?.delete()
        audioSampleFile = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "SearchFragment"
        private const val ARTIST_PROFILE_PREFIX = "artist:"
        private const val SEARCH_DEBOUNCE_MS = 500L
        private const val AUDIO_SAMPLE_DURATION_MS = 8_000L
        private const val AUDIO_SAMPLE_RATE = 44_100
        private const val AUDIO_ENCODING_BIT_RATE = 128_000
        private const val AUDIO_SAMPLE_FILE_PREFIX = "audio_search_"
        private const val AUDIO_SAMPLE_FILE_EXTENSION = "m4a"
        private const val AUDIO_IDLE_ICON_COLOR = "#BDBDBD"
        private const val AUDIO_RECORDING_ICON_COLOR = "#FF9800"
        private var sessionLatestQuery: String = ""
    }
}
