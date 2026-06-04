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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.FragmentSearchBinding
import com.example.music_app.main.MainActivity
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.player.PlayerFragment
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SearchFragment : Fragment(R.layout.fragment_search) {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var searchAdapter: SearchAdapter

    private val allSongs = mutableListOf<Song>()

    private var currentTab = SearchTab.ALL

    enum class SearchTab {
        ALL,
        TRACKS,
        PROFILES,
        PLAYLISTS,
        ALBUMS
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentSearchBinding.bind(view)

        setupRecyclerView()
        setupSearchBox()
        setupTabs()
        selectTab(SearchTab.ALL)
        loadSongsFromFirebase()
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter { song ->
            PlayerManager.play(song)

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, PlayerFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.rvSearchSongs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }
    }

    private fun setupSearchBox() {
        binding.btnCancel.visibility = View.GONE
        binding.tabContainer.visibility = View.GONE
        binding.tvSearchSectionTitle.text = "Recently searched"

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
            ) {
            }

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                val keyword = s.toString().trim()

                binding.btnCancel.visibility =
                    if (keyword.isNotEmpty()) View.VISIBLE else View.GONE

                binding.tabContainer.visibility =
                    if (keyword.isNotEmpty()) View.VISIBLE else View.GONE

                binding.tvSearchSectionTitle.text =
                    if (keyword.isNotEmpty()) getTitleByTab(currentTab) else "Recently searched"

                filterSongs(keyword)
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })

        binding.btnCancel.setOnClickListener {
            binding.edtSearch.text.clear()
            binding.edtSearch.clearFocus()
            hideKeyboard()

            binding.btnCancel.visibility = View.GONE
            binding.tabContainer.visibility = View.GONE
            binding.tvSearchSectionTitle.text = "Recently searched"

            searchAdapter.setData(emptyList())
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

        binding.tabAlbums.setOnClickListener {
            selectTab(SearchTab.ALBUMS)
        }
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

            SearchTab.ALBUMS -> {
                binding.tabAlbums.setBackgroundResource(R.drawable.bg_search_tab_selected)
                binding.tabAlbums.setTextColor(Color.BLACK)
            }
        }

        val keyword = binding.edtSearch.text.toString().trim()

        binding.tvSearchSectionTitle.text =
            if (keyword.isNotEmpty()) getTitleByTab(tab) else "Recently searched"

        filterSongs(keyword)
    }

    private fun resetTabStyle() {
        val tabs = listOf(
            binding.tabAll,
            binding.tabTracks,
            binding.tabProfiles,
            binding.tabPlaylists,
            binding.tabAlbums
        )

        tabs.forEach { tab ->
            tab.setBackgroundResource(R.drawable.bg_search_tab_normal)
            tab.setTextColor(Color.WHITE)
        }
    }

    private fun getTitleByTab(tab: SearchTab): String {
        return when (tab) {
            SearchTab.ALL -> "All results"
            SearchTab.TRACKS -> "Tracks"
            SearchTab.PROFILES -> "Profiles"
            SearchTab.PLAYLISTS -> "Playlists"
            SearchTab.ALBUMS -> "Albums"
        }
    }

    private fun loadSongsFromFirebase() {
        lifecycleScope.launch {
            try {
                val snapshot = FirebaseFirestore.getInstance()
                    .collection("songs")
                    .get()
                    .await()

                allSongs.clear()

                val songs = snapshot.documents.mapNotNull { document ->
                    val song = document.toObject(Song::class.java)

                    song?.copy(
                        id = document.id
                    )
                }

                allSongs.addAll(songs)

                val keyword = binding.edtSearch.text.toString().trim()
                filterSongs(keyword)

            } catch (e: Exception) {
                if (_binding != null) {
                    Toast.makeText(
                        requireContext(),
                        "Không thể tải dữ liệu bài hát: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun filterSongs(keyword: String) {
        if (keyword.isEmpty()) {
            searchAdapter.setData(emptyList())
            return
        }

        val result = when (currentTab) {
            SearchTab.ALL -> {
                allSongs.filter { song ->
                    song.title.contains(keyword, ignoreCase = true) ||
                            song.artist.contains(keyword, ignoreCase = true)
                }
            }

            SearchTab.TRACKS -> {
                allSongs.filter { song ->
                    song.title.contains(keyword, ignoreCase = true)
                }
            }

            SearchTab.PROFILES -> {
                allSongs.filter { song ->
                    song.artist.contains(keyword, ignoreCase = true)
                }
            }

            SearchTab.PLAYLISTS -> {
                emptyList()
            }

            SearchTab.ALBUMS -> {
                emptyList()
            }
        }

        searchAdapter.setData(result)
    }

    private fun hideKeyboard() {
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        imm.hideSoftInputFromWindow(binding.edtSearch.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}