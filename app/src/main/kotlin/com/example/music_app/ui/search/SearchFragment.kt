package com.example.music_app.ui.search

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.databinding.FragmentSearchBinding
import com.example.music_app.main.MainActivity
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.player.PlayerFragment
import com.example.music_app.ui.song.SongAdapter

class SearchFragment : Fragment(R.layout.fragment_search) {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels()

    private lateinit var adapter: SongAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentSearchBinding.bind(view)

        setupRecyclerView()
        setupSearchBox()
        observeViewModel()

        viewModel.loadSongs()
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter(
            onItemClick = { song ->
                PlayerManager.play(song)

                hideKeyboard()
                (requireActivity() as? MainActivity)?.setFooterVisible(false)
                (requireActivity() as? MainActivity)?.setMiniPlayerVisible(false)

                parentFragmentManager.commit {
                    replace(R.id.fragmentContainer, PlayerFragment.newInstance(song.id))
                    addToBackStack(null)
                }
            }
        )

        binding.rvSearchSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSearchSongs.adapter = adapter
    }

    private fun setupSearchBox() {
        binding.btnCancel.visibility = View.GONE
        binding.tabContainer.visibility = View.GONE

        binding.edtSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.tabContainer.visibility = View.VISIBLE
                (requireActivity() as? MainActivity)?.setFooterVisible(false)
                (requireActivity() as? MainActivity)?.setMiniPlayerVisible(false)
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
                val keyword = s?.toString().orEmpty()

                binding.btnCancel.visibility =
                    if (keyword.isBlank()) View.GONE else View.VISIBLE

                binding.tvSearchSectionTitle.text =
                    if (keyword.isBlank()) "Recently searched" else "Tracks"

                viewModel.search(keyword)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnCancel.setOnClickListener {
            binding.edtSearch.text?.clear()
            binding.edtSearch.clearFocus()
            binding.btnCancel.visibility = View.GONE
            binding.tabContainer.visibility = View.GONE
            binding.tvSearchSectionTitle.text = "Recently searched"

            hideKeyboard()

            (requireActivity() as? MainActivity)?.setFooterVisible(true)
            (requireActivity() as? MainActivity)?.setMiniPlayerVisible(true)

            viewModel.search("")
        }
    }

    private fun observeViewModel() {
        viewModel.searchResults.observe(viewLifecycleOwner) { songs ->
            adapter.setData(songs)
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hideKeyboard() {
        val inputMethodManager =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        inputMethodManager.hideSoftInputFromWindow(binding.edtSearch.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()

        if (binding.edtSearch.hasFocus()) {
            (requireActivity() as? MainActivity)?.setFooterVisible(false)
            (requireActivity() as? MainActivity)?.setMiniPlayerVisible(false)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}