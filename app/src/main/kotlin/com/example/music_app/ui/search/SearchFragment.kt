package com.example.music_app.ui.search

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.databinding.FragmentSearchBinding
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.player.PlayerFragment
import com.example.music_app.ui.song.SongAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SongAdapter

    private val repository = SongRepository()

    private var allSongs: List<Song> = emptyList()
    private var recentSongs: List<Song> = emptyList()

    private var keyboardListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var isKeyboardVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupRecyclerView()
        setupSearchActions()
        setupTabs()
        setupKeyboardListener()
        loadSongs()
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter { song ->
            playSong(song)
        }

        binding.rvSearchSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSearchSongs.adapter = adapter
    }

    private fun setupSearchActions() {
        binding.btnCancel.setOnClickListener {
            binding.edtSearch.text?.clear()
            binding.btnCancel.visibility = View.GONE
            showRecentlySearched()
            hideKeyboard()
        }

        binding.edtSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.edtSearch.text.toString().trim()
                searchSongs(query, showTabs = true)
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
                val query = s?.toString()?.trim().orEmpty()

                binding.btnCancel.visibility =
                    if (query.isNotEmpty()) View.VISIBLE else View.GONE

                if (query.isEmpty()) {
                    showRecentlySearched()
                } else {
                    searchSongs(query, showTabs = false)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupTabs() {
        val tabs = listOf(
            binding.tabAll,
            binding.tabTracks,
            binding.tabProfiles,
            binding.tabPlaylists,
            binding.tabAlbums
        )

        tabs.forEach { tab ->
            tab.setOnClickListener {
                selectTab(tab, tabs)
            }
        }
    }

    private fun selectTab(selectedTab: TextView, tabs: List<TextView>) {
        tabs.forEach { tab ->
            tab.setTextColor(Color.parseColor("#AFAFAF"))
        }

        selectedTab.setTextColor(Color.WHITE)

        val query = binding.edtSearch.text.toString().trim()
        searchSongs(query, showTabs = true)
    }

    private fun setupKeyboardListener() {
        val rootView = binding.root

        keyboardListener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)

            val screenHeight = rootView.rootView.height
            val keyboardHeight = screenHeight - rect.bottom

            val keyboardNowVisible = keyboardHeight > screenHeight * 0.15

            if (keyboardNowVisible != isKeyboardVisible) {
                isKeyboardVisible = keyboardNowVisible

                if (isKeyboardVisible) {
                    hideFooter()
                } else {
                    showFooter()
                }
            }
        }

        rootView.viewTreeObserver.addOnGlobalLayoutListener(keyboardListener)
    }

    private fun loadSongs() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val songs = repository.getAllSongs()

                withContext(Dispatchers.Main) {
                    allSongs = songs
                    recentSongs = songs.take(6)
                    showRecentlySearched()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Không tải được dữ liệu tìm kiếm",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showRecentlySearched() {
        binding.tabContainer.visibility = View.GONE
        binding.tvSearchSectionTitle.visibility = View.VISIBLE
        binding.tvSearchSectionTitle.text = "Recently searched"

        adapter.setData(recentSongs)
    }

    private fun searchSongs(query: String, showTabs: Boolean) {
        if (query.isBlank()) {
            showRecentlySearched()
            return
        }

        binding.tabContainer.visibility =
            if (showTabs) View.VISIBLE else View.GONE

        binding.tvSearchSectionTitle.visibility = View.VISIBLE
        binding.tvSearchSectionTitle.text =
            if (showTabs) "Top Result" else "Search results"

        val result = allSongs.filter { song ->
            song.title.contains(query, ignoreCase = true) ||
                    song.artist.contains(query, ignoreCase = true)
        }

        adapter.setData(result)
    }

    private fun playSong(song: Song) {
        PlayerManager.play(song)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            repository.saveRecentlyPlayed(song)
        }

        hideKeyboard()
        showFooter()

        parentFragmentManager.commit {
            replace(R.id.fragmentContainer, PlayerFragment.newInstance(song.id))
            addToBackStack(null)
        }
    }

    private fun hideKeyboard() {
        val inputMethodManager =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        inputMethodManager.hideSoftInputFromWindow(binding.edtSearch.windowToken, 0)
        binding.edtSearch.clearFocus()
    }

    private fun hideFooter() {
        requireActivity().findViewById<View>(R.id.appFooter).visibility = View.GONE
        requireActivity().findViewById<View>(R.id.miniPlayer).visibility = View.GONE
    }

    private fun showFooter() {
        requireActivity().findViewById<View>(R.id.appFooter).visibility = View.VISIBLE

        if (PlayerManager.currentSong.value != null) {
            requireActivity().findViewById<View>(R.id.miniPlayer).visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        keyboardListener?.let {
            binding.root.viewTreeObserver.removeOnGlobalLayoutListener(it)
        }

        showFooter()
        _binding = null
        super.onDestroyView()
    }
}