package com.example.music_app.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.music_app.base.BaseActivity
import com.example.music_app.data.model.UserRole
import com.example.music_app.data.repository.AuthRepository
import com.example.music_app.data.repository.UserRepository
import com.example.music_app.player.state.PlayerInteractionState
import com.example.music_app.databinding.ActivityMainBinding
import com.example.music_app.player.PlayerManager
import com.example.music_app.service.MusicService
import com.example.music_app.ui.admin.AdminDashboardFragment
import com.example.music_app.ui.auth.LoginActivity
import com.example.music_app.ui.comment.CommentFragment
import com.example.music_app.ui.home.HomeFragment
import com.example.music_app.ui.library.LibraryFragment
import com.example.music_app.ui.player.PlayerFragment
import com.example.music_app.ui.player.MiniPlayerViewModel
import com.example.music_app.ui.profile.ProfileFragment
import com.example.music_app.ui.search.SearchFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.music_app.ui.admin.AdminModerationFragment
import com.example.music_app.ui.admin.AdminReportFragment
import com.example.music_app.ui.admin.AdminCommentModerationFragment

class MainActivity : BaseActivity<ActivityMainBinding>() {

    private val authRepository = AuthRepository()
    private val miniPlayerViewModel: MiniPlayerViewModel by viewModels()
    private val userRepository = UserRepository()

    private lateinit var miniPlayerController: MiniPlayerPresentationController

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startMusicService()
            }
        }

    private lateinit var footerNavigation: FooterNavigationController

    override fun getViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUser = authRepository.getCurrentUser()

        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        PlayerManager.init(this)

        setupFooter()
        setupMiniPlayer()

        supportFragmentManager.addOnBackStackChangedListener {
            updateMainChromeVisibility()
        }

        requestNotificationPermissionIfNeeded()

        if (savedInstanceState == null) {
            openStartFragmentByRole()
        }
    }

    private fun openStartFragmentByRole() {
        val currentUser = authRepository.getCurrentUser()

        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = userRepository.getUserProfile(currentUser.uid).getOrNull()

                withContext(Dispatchers.Main) {
                    if (user?.role == UserRole.ADMIN) {
                        openAdminDashboardFragment()
                    } else {
                        openHomeFragment()
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    openHomeFragment()
                }
            }
        }
    }

    private fun openAdminDashboardFragment() {
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, AdminDashboardFragment())
            .commit()

        setFooterVisible(false)
        miniPlayerController.hideAll()
    }

    private fun openHomeFragment() {
        openFooterTab(FooterTab.HOME)
    }

    fun updateMainChromeVisibility() {
        val currentFragment =
            supportFragmentManager.findFragmentById(binding.fragmentContainer.id)

        val shouldHideMainChrome =
            currentFragment is PlayerFragment ||
                    currentFragment is CommentFragment ||
                    currentFragment is AdminDashboardFragment ||
                    currentFragment is AdminReportFragment ||
                    currentFragment is AdminCommentModerationFragment ||
                    currentFragment is AdminModerationFragment

        if (shouldHideMainChrome) {
            setFooterVisible(false)
            miniPlayerController.hideAll()
        } else {
            setFooterVisible(true)
            updateMiniPlayerVisibility()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (granted) {
                startMusicService()
            } else {
                requestNotificationPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        } else {
            startMusicService()
        }
    }

    @OptIn(UnstableApi::class)
   fun startMusicService() {
        val intent = Intent(this, MusicService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun setupFooter() {
        footerNavigation = FooterNavigationController(
            footer = binding.appFooter,
            homeButton = binding.btnHome,
            searchButton = binding.btnSearch,
            libraryButton = binding.btnLibrary,
            profileButton = binding.btnProfile,
            onTabSelected = ::openFooterTab
        )
        footerNavigation.bind()
    }

    private fun openFooterTab(tab: FooterTab) {
        val fragment = when (tab) {
            FooterTab.HOME -> HomeFragment()
            FooterTab.SEARCH -> SearchFragment()
            FooterTab.LIBRARY -> LibraryFragment()
            FooterTab.PROFILE -> ProfileFragment()
        }

        openMainFragment(fragment)
        footerNavigation.select(tab)
        footerNavigation.setVisible(true)
        updateMiniPlayerVisibility()
    }

    private fun setupMiniPlayer() {
        miniPlayerController = MiniPlayerPresentationController(
            miniPlayer = binding.miniPlayer,
            ghostMiniPlayer = binding.miniPlayerGhost,
            onSongClick = { song -> openPlayerFragment(song.id) },
            onPlayPauseClick = PlayerManager::toggle,
            onLikeClick = miniPlayerViewModel::toggleLike,
            onFollowClick = miniPlayerViewModel::toggleFollow
        )
        miniPlayerController.bind()

        PlayerManager.playlistSongs.observe(this) { songs ->
            miniPlayerController.renderPlaylist(
                songs = songs,
                currentIndex = PlayerManager.currentIndex.value ?: -1
            )
        }

        PlayerManager.currentSong.observe(this) { song ->
            if (song == null) {
                miniPlayerController.renderSong(song = null)

                return@observe
            }

            miniPlayerController.renderSong(
                song = song,
                isLiked = PlayerInteractionState.songState(song.id)?.liked ?: false,
                isFollowed = PlayerInteractionState.artistState(song.uploaderId)?.followed ?: false,
                currentIndex = PlayerManager.currentIndex.value ?: -1
            )

            miniPlayerViewModel.loadLikeState(song)
            miniPlayerViewModel.loadFollowState(song)
            updateMiniPlayerVisibility()
        }

        PlayerManager.currentIndex.observe(this) { index ->
            miniPlayerController.renderCurrentPage(index, smoothScroll = true)
        }

        PlayerManager.isPlaying.observe(this) { isPlaying ->
            miniPlayerController.renderPlaybackState(isPlaying)
            updateMiniPlayerVisibility()
        }

        miniPlayerViewModel.errorMessageResId.observe(this) { messageResId ->
            messageResId ?: return@observe
            Toast.makeText(this, getString(messageResId), Toast.LENGTH_SHORT).show()
            miniPlayerViewModel.clearErrorMessage()
        }

        miniPlayerViewModel.successMessageResId.observe(this) { messageResId ->
            messageResId ?: return@observe
            Toast.makeText(this, getString(messageResId), Toast.LENGTH_SHORT).show()
            miniPlayerViewModel.clearSuccessMessage()
        }

        miniPlayerViewModel.isFollowButtonVisible.observe(this) { visible ->
            miniPlayerController.setFollowButtonVisible(visible)
        }

        PlayerInteractionState.songLikeUpdates.observe(this) { state ->
            if (!miniPlayerController.isShowingSong(state.songId)) return@observe

            miniPlayerController.renderLikeState(state.liked)
        }

        PlayerInteractionState.artistFollowUpdates.observe(this) { state ->
            if (!miniPlayerController.isShowingUploader(state.userId)) return@observe

            miniPlayerController.setFollowButtonVisible(true)
            miniPlayerController.renderFollowState(state.followed)
        }
    }

    private fun openMainFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, fragment)
            .commit()

        binding.fragmentContainer.post {
            updateMainChromeVisibility()
        }
    }

    private fun openPlayerFragment(songId: String) {
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, PlayerFragment.newInstance(songId))
            .addToBackStack(null)
            .commit()

        binding.fragmentContainer.post {
            updateMainChromeVisibility()
        }
    }

    private fun updateMiniPlayerVisibility() {
        val hasSong = PlayerManager.currentSong.value != null

        val currentFragment =
            supportFragmentManager.findFragmentById(binding.fragmentContainer.id)

        val isPlayerScreen = currentFragment is PlayerFragment
        val isCommentScreen = currentFragment is CommentFragment
        val isAdminDashboard = currentFragment is AdminDashboardFragment

        miniPlayerController.setVisible(
            hasSong && !isPlayerScreen && !isCommentScreen && !isAdminDashboard
        )
    }

    fun setMiniPlayerVisible(visible: Boolean) {
        val hasSong = PlayerManager.currentSong.value != null

        miniPlayerController.setVisible(visible && hasSong)
    }

    fun setFooterVisible(visible: Boolean) {
        footerNavigation.setVisible(visible)
    }
}
