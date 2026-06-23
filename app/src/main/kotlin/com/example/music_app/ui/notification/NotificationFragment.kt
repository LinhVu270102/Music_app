package com.example.music_app.ui.notification

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.databinding.FragmentNotificationBinding

class NotificationFragment : Fragment(R.layout.fragment_notification) {

    private var _binding: FragmentNotificationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NotificationViewModel by viewModels()
    private lateinit var adapter: NotificationAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentNotificationBinding.bind(view)

        adapter = NotificationAdapter { notification ->
            viewModel.markRead(notification)
        }

        binding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNotifications.adapter = adapter
        binding.swipeRefreshNotifications.setOnRefreshListener {
            viewModel.loadNotifications()
        }
        binding.btnMarkAllRead.setOnClickListener {
            viewModel.markAllRead()
        }

        viewModel.notifications.observe(viewLifecycleOwner) { notifications ->
            adapter.setData(notifications)
            binding.tvEmptyNotifications.isVisible = notifications.isEmpty()
            binding.btnMarkAllRead.isVisible = notifications.any { notification ->
                !notification.isRead
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefreshNotifications.isRefreshing = isLoading
            binding.progressNotifications.isVisible = isLoading && adapter.itemCount == 0
        }

        viewModel.errorMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId ?: return@observe
            Toast.makeText(requireContext(), getString(messageResId), Toast.LENGTH_SHORT).show()
            viewModel.clearErrorMessage()
        }

        viewModel.loadNotifications()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
