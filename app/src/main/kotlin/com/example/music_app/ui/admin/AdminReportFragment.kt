package com.example.music_app.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.databinding.FragmentAdminReportBinding

class AdminReportFragment : Fragment(R.layout.fragment_admin_report) {

    private var _binding: FragmentAdminReportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminReportViewModel by viewModels()

    private lateinit var adapter: AdminReportAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentAdminReportBinding.bind(view)

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadReports()
    }

    private fun setupRecyclerView() {
        adapter = AdminReportAdapter(
            onResolve = { report ->
                viewModel.resolveReport(report)
            },
            onReject = { report ->
                viewModel.rejectReport(report)
            },
            onHideTarget = { report ->
                viewModel.hideReportedTarget(report)
            }
        )

        binding.recyclerReports.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerReports.adapter = adapter
    }

    private fun setupListeners() {
        binding.swipeRefreshAdminReports.setOnRefreshListener {
            viewModel.loadReports()
        }

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnReload.setOnClickListener {
            viewModel.loadReports()
        }
    }

    private fun observeViewModel() {
        viewModel.reports.observe(viewLifecycleOwner) { reports ->
            adapter.submitList(reports)

            binding.txtEmptyReports.visibility =
                if (reports.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressLoading.visibility =
                if (isLoading) View.VISIBLE else View.GONE
            binding.swipeRefreshAdminReports.isRefreshing = isLoading
        }

        viewModel.messageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                Toast.makeText(requireContext(), getString(it), Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
                binding.swipeRefreshAdminReports.isRefreshing = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
