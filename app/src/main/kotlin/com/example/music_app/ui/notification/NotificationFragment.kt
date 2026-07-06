package com.example.music_app.ui.notification

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.data.model.AppNotification
import com.example.music_app.data.model.enums.AppNotificationTargetType
import com.example.music_app.databinding.FragmentNotificationBinding
import com.example.music_app.main.MainActivity
import com.example.music_app.ui.comment.CommentFragment
import com.example.music_app.ui.player.PlayerFragment
import com.example.music_app.ui.profile.ArtistProfileFragment

class NotificationFragment : Fragment(R.layout.fragment_notification) {

    private var _binding: FragmentNotificationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NotificationViewModel by viewModels()
    private lateinit var adapter: NotificationAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentNotificationBinding.bind(view)

        adapter = NotificationAdapter { notification ->
            viewModel.markRead(notification)
            openNotificationTarget(notification)
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

    private fun openNotificationTarget(notification: AppNotification) {
        val fragment = when (notification.targetKind) {
            AppNotificationTargetType.SONG -> {
                val songId = notification.relatedSongId.ifBlank { notification.targetId }
                if (songId.isBlank()) return
                PlayerFragment.newInstance(songId)
            }

            AppNotificationTargetType.COMMENT -> {
                val songId = notification.relatedSongId
                if (songId.isBlank()) return
                CommentFragment.newInstance(songId)
            }

            AppNotificationTargetType.USER -> {
                if (notification.targetId.isBlank()) return
                ArtistProfileFragment.newInstance(notification.targetId)
            }

            else -> return
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()

        (requireActivity() as? MainActivity)?.updateMainChromeVisibility()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
