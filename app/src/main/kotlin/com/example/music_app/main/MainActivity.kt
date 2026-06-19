package com.example.music_app.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.music_app.R
import com.example.music_app.base.BaseActivity
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.UserRole
import com.example.music_app.data.remote.FirebaseService
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.databinding.ActivityMainBinding
import com.example.music_app.player.PlayerManager
import com.example.music_app.service.MusicService
import com.example.music_app.ui.admin.AdminDashboardFragment
import com.example.music_app.ui.auth.LoginActivity
import com.example.music_app.ui.comment.CommentFragment
import com.example.music_app.ui.home.HomeFragment
import com.example.music_app.ui.library.LibraryFragment
import com.example.music_app.ui.player.PlayerFragment
import com.example.music_app.ui.profile.ProfileFragment
import com.example.music_app.ui.search.SearchFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.viewpager2.widget.ViewPager2
import com.example.music_app.ui.home.MiniPlayerPagerAdapter

class MainActivity : BaseActivity<ActivityMainBinding>() {

    private val songRepository = SongRepository()
    private val firebaseService = FirebaseService(FirebaseFirestore.getInstance())
    private var currentMiniSong: Song? = null
    private var isCurrentSongLiked = false
    private lateinit var miniPlayerPagerAdapter: MiniPlayerPagerAdapter
    private var ignoreMiniPagerCallback = false
    private var miniPagerUserDragging = false

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startMusicService()
            }
        }

    enum class FooterTab {
        HOME, SEARCH, LIBRARY, PROFILE
    }

    override fun getViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUser = FirebaseAuth.getInstance().currentUser

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
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = firebaseService.getUserById(currentUser.uid)

                withContext(Dispatchers.Main) {
                    if (user?.role == UserRole.ADMIN) {
                        openAdminDashboardFragment()
                    } else {
                        openHomeFragment()
                    }
                }
            } catch (e: Exception) {
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

        binding.appFooter.visibility = View.GONE
        binding.miniPlayer.root.visibility = View.GONE
    }

    private fun openHomeFragment() {
        openMainFragment(HomeFragment())
        highlightFooter(FooterTab.HOME)
        setFooterVisible(true)
        updateMiniPlayerVisibility()
    }

    fun updateMainChromeVisibility() {
        val currentFragment =
            supportFragmentManager.findFragmentById(binding.fragmentContainer.id)

        val shouldHideMainChrome =
            currentFragment is PlayerFragment ||
                    currentFragment is CommentFragment ||
                    currentFragment is AdminDashboardFragment

        if (shouldHideMainChrome) {
            binding.appFooter.visibility = View.GONE
            binding.miniPlayer.root.visibility = View.GONE
        } else {
            binding.appFooter.visibility = View.VISIBLE
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
    private fun startMusicService() {
        val intent = Intent(this, MusicService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun setupFooter() {
        binding.btnHome.setOnClickListener {
            openMainFragment(HomeFragment())
            highlightFooter(FooterTab.HOME)
            setFooterVisible(true)
            updateMiniPlayerVisibility()
        }

        binding.btnSearch.setOnClickListener {
            openMainFragment(SearchFragment())
            highlightFooter(FooterTab.SEARCH)
            setFooterVisible(true)
            updateMiniPlayerVisibility()
        }

        binding.btnLibrary.setOnClickListener {
            openMainFragment(LibraryFragment())
            highlightFooter(FooterTab.LIBRARY)
            setFooterVisible(true)
            updateMiniPlayerVisibility()
        }

        binding.btnProfile.setOnClickListener {
            openMainFragment(ProfileFragment())
            highlightFooter(FooterTab.PROFILE)
            setFooterVisible(true)
            updateMiniPlayerVisibility()
        }
    }

    private fun setupMiniPlayer() {
        binding.miniPlayer.root.visibility = View.GONE
        updateMiniPlayerLikeIcon()

        miniPlayerPagerAdapter = MiniPlayerPagerAdapter { song ->
            openPlayerFragment(song.id)
        }

        binding.miniPlayer.vpMiniPlayer.adapter = miniPlayerPagerAdapter
        binding.miniPlayer.vpMiniPlayer.offscreenPageLimit = 1

        binding.miniPlayer.vpMiniPlayer.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {

                override fun onPageScrollStateChanged(state: Int) {
                    super.onPageScrollStateChanged(state)

                    when (state) {
                        ViewPager2.SCROLL_STATE_DRAGGING -> {
                            miniPagerUserDragging = true
                        }

                        ViewPager2.SCROLL_STATE_IDLE -> {
                            miniPagerUserDragging = false
                        }
                    }
                }

                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)

                    if (ignoreMiniPagerCallback) return
                    if (!miniPagerUserDragging) return

                    PlayerManager.playSongAt(position)
                }
            }
        )

        PlayerManager.playlistSongs.observe(this) { songs ->
            miniPlayerPagerAdapter.submitList(songs) {
                val index = PlayerManager.currentIndex.value ?: -1
                setMiniPlayerPage(index, smoothScroll = false)
            }
        }

        PlayerManager.currentSong.observe(this) { song ->
            if (song == null) {
                currentMiniSong = null
                isCurrentSongLiked = false
                binding.miniPlayer.root.visibility = View.GONE
                updateMiniPlayerLikeIcon()
                return@observe
            }

            currentMiniSong = song

            loadMiniPlayerLikeState(song.id)
            updateMiniPlayerVisibility()

            val index = PlayerManager.currentIndex.value ?: -1
            setMiniPlayerPage(index, smoothScroll = true)
        }

        PlayerManager.currentIndex.observe(this) { index ->
            setMiniPlayerPage(index, smoothScroll = true)
        }

        PlayerManager.isPlaying.observe(this) { isPlaying ->
            val icon = if (isPlaying) {
                R.drawable.ic_pause
            } else {
                R.drawable.ic_play_button_circle
            }

            binding.miniPlayer.btnPlayPause.setImageResource(icon)
            updateMiniPlayerVisibility()
        }

        binding.miniPlayer.btnPlayPause.setOnClickListener {
            PlayerManager.toggle()
        }

        binding.miniPlayer.btnLike.setOnClickListener {
            toggleMiniPlayerLike()
        }
    }

    private fun setMiniPlayerPage(
        index: Int,
        smoothScroll: Boolean
    ) {
        if (index < 0) return
        if (!::miniPlayerPagerAdapter.isInitialized) return
        if (miniPlayerPagerAdapter.itemCount == 0) return
        if (index >= miniPlayerPagerAdapter.itemCount) return
        if (binding.miniPlayer.vpMiniPlayer.currentItem == index) return

        ignoreMiniPagerCallback = true

        binding.miniPlayer.vpMiniPlayer.setCurrentItem(
            index,
            smoothScroll
        )

        binding.miniPlayer.vpMiniPlayer.post {
            ignoreMiniPagerCallback = false
        }
    }

    private fun loadMiniPlayerLikeState(songId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val liked = songRepository.isSongLiked(songId)

                withContext(Dispatchers.Main) {
                    isCurrentSongLiked = liked
                    updateMiniPlayerLikeIcon()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    isCurrentSongLiked = false
                    updateMiniPlayerLikeIcon()
                }
            }
        }
    }

    private fun toggleMiniPlayerLike() {
        val song = currentMiniSong ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val liked = songRepository.toggleLikeSong(song)

                withContext(Dispatchers.Main) {
                    isCurrentSongLiked = liked
                    updateMiniPlayerLikeIcon()

                    val message = if (liked) {
                        getString(R.string.added_to_your_likes)
                    } else {
                        getString(R.string.removed_from_your_likes)
                    }

                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        e.message ?: getString(R.string.update_like_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun updateMiniPlayerLikeIcon() {
        binding.miniPlayer.btnLike.alpha =
            if (isCurrentSongLiked) {
                1f
            } else {
                0.4f
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

        binding.miniPlayer.root.visibility =
            if (
                hasSong &&
                !isPlayerScreen &&
                !isCommentScreen &&
                !isAdminDashboard
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    fun setMiniPlayerVisible(visible: Boolean) {
        val hasSong = PlayerManager.currentSong.value != null

        binding.miniPlayer.root.visibility =
            if (visible && hasSong) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    fun setFooterVisible(visible: Boolean) {
        binding.appFooter.visibility =
            if (visible) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun highlightFooter(activeTab: FooterTab) {
        resetFooter()

        when (activeTab) {
            FooterTab.HOME -> activeFooter(binding.btnHome)
            FooterTab.SEARCH -> activeFooter(binding.btnSearch)
            FooterTab.LIBRARY -> activeFooter(binding.btnLibrary)
            FooterTab.PROFILE -> activeFooter(binding.btnProfile)
        }
    }

    private fun resetFooter() {
        val buttons = listOf(
            binding.btnHome,
            binding.btnSearch,
            binding.btnLibrary,
            binding.btnProfile
        )

        buttons.forEach { button ->
            button.scaleX = 1f
            button.scaleY = 1f
            button.alpha = 0.5f
        }
    }

    private fun activeFooter(button: View) {
        button.scaleX = 1.2f
        button.scaleY = 1.2f
        button.alpha = 1f
    }
}