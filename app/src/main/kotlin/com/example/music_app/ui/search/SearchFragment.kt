package com.example.music_app.ui.search

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.databinding.FragmentSearchBinding

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SearchAdapter
    private val viewModel: SearchViewModel by viewModels()

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
        observeViewModel()
        setupSearchFocus()
        setupSearchInput()
        setupButtons()
    }

    private fun setupRecyclerView() {
        adapter = SearchAdapter { result ->
            Toast.makeText(
                requireContext(),
                "Bạn chọn: $result",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.searchResults.layoutManager = LinearLayoutManager(requireContext())
        binding.searchResults.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.results.observe(viewLifecycleOwner) { results ->
            adapter.setData(results)
        }

        viewModel.showReturn.observe(viewLifecycleOwner) { visible ->
            binding.btnReturn.visibility = if (visible) View.VISIBLE else View.GONE
        }

        viewModel.showCancel.observe(viewLifecycleOwner) { visible ->
            binding.btnCancel.visibility = if (visible) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(
                    requireContext(),
                    "Lỗi: $it",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupSearchFocus() {
        binding.edtSearch.setOnFocusChangeListener { _, hasFocus ->
            viewModel.onFocusChanged(hasFocus)

            val footer = requireActivity().findViewById<View>(R.id.appFooter)
            footer.visibility = if (hasFocus) View.GONE else View.VISIBLE

            if (hasFocus) {
                viewModel.loadSuggestions()
            }
        }
    }

    private fun setupSearchInput() {
        binding.edtSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateQuery(s.toString().trim())
            }

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {}
        })
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener {
            viewModel.clearQuery()
            binding.edtSearch.text.clear()
        }

        binding.btnReturn.setOnClickListener {
            binding.edtSearch.clearFocus()
            hideKeyboard()

            val currentQuery = viewModel.query.value
            if (!currentQuery.isNullOrEmpty()) {
                viewModel.updateQuery(currentQuery)
            }
        }
    }

    private fun hideKeyboard() {
        val inputMethodManager =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        inputMethodManager.hideSoftInputFromWindow(binding.edtSearch.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        requireActivity()
            .findViewById<View>(R.id.appFooter)
            ?.visibility = View.VISIBLE

        _binding = null
    }
}