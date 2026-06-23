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
import com.example.music_app.player.state.ArtistFollowState
import com.example.music_app.player.state.PlayerInteractionState
import com.example.music_app.player.state.SongLikeState
import com.example.music_app.data.repository.SoundCloudSocialRepository
import com.example.music_app.databinding.ActivityMainBinding
import com.example.music_app.player.PlayerManager
import com.example.music_app.service.MusicService
import com.example.music_app.ui.admin.AdminDashboardFragment
import com.example.music_app.ui.auth.LoginActivity
import com.example.music_app.ui.comment.CommentFragment
import com.example.music_app.ui.home.HomeFragment
import com.example.music_app.ui.home.MiniPlayerPagerAdapter
import com.example.music_app.ui.library.LibraryFragment
import com.example.music_app.ui.player.PlayerFragment
import com.example.music_app.ui.profile.ProfileFragment
import com.example.music_app.ui.search.SearchFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import com.example.music_app.ui.admin.AdminModerationFragment
import com.example.music_app.ui.admin.AdminReportFragment
import com.example.music_app.ui.admin.AdminCommentModerationFragment

class MainActivity : BaseActivity<ActivityMainBinding>() {

    private val songRepository = SongRepository()
    private val soundCloudSocialRepository = SoundCloudSocialRepository()
    private val firebaseService = FirebaseService(FirebaseFirestore.getInstance())

    private var currentMiniSong: Song? = null
    private var isCurrentSongLiked = false
    private var isCurrentUploaderFollowed = false

    private lateinit var miniPlayerPagerAdapter: MiniPlayerPagerAdapter
    private lateinit var ghostMiniPlayerPagerAdapter: MiniPlayerPagerAdapter

    private var ignoreMiniPagerCallback = false
    private var pendingMiniEnterDirection = 0
    private var pendingMiniTargetSong: Song? = null
    private var pendingMiniTargetFromPlaylist = false
    private var isMiniTailAnimating = false

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startMusicService()
            }
        }

    enum class FooterTab {
        HOME,
        SEARCH,
        LIBRARY,
        PROFILE
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

        binding.appFooter.visibility = View.GONE
        binding.miniPlayer.root.visibility = View.GONE
        binding.miniPlayerGhost.root.visibility = View.GONE
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
                    currentFragment is AdminDashboardFragment ||
                    currentFragment is AdminReportFragment ||
                    currentFragment is AdminCommentModerationFragment ||
                    currentFragment is AdminModerationFragment

        if (shouldHideMainChrome) {
            binding.appFooter.visibility = View.GONE
            binding.miniPlayer.root.visibility = View.GONE
            binding.miniPlayerGhost.root.visibility = View.GONE
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
   fun startMusicService() {
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
        binding.miniPlayerGhost.root.visibility = View.GONE

        updateMiniPlayerLikeIcon()

        miniPlayerPagerAdapter = MiniPlayerPagerAdapter(
            onSongClick = { song ->
                openPlayerFragment(song.id)
            },
            onDrag = { diffX ->
                dragMiniPlayersTogether(diffX)
            },
            onSwipeLeft = {
                animateMiniPlayersOutThenChange(direction = -1)
            },
            onSwipeRight = {
                animateMiniPlayersOutThenChange(direction = 1)
            },
            onCancelSwipe = {
                resetMiniPlayersPosition()
            }
        )

        ghostMiniPlayerPagerAdapter = MiniPlayerPagerAdapter(
            onSongClick = {},
            onDrag = {},
            onSwipeLeft = {},
            onSwipeRight = {},
            onCancelSwipe = {}
        )

        binding.miniPlayer.vpMiniPlayer.adapter = miniPlayerPagerAdapter
        binding.miniPlayer.vpMiniPlayer.offscreenPageLimit = 1
        binding.miniPlayer.vpMiniPlayer.isUserInputEnabled = false

        binding.miniPlayerGhost.vpMiniPlayer.adapter = ghostMiniPlayerPagerAdapter
        binding.miniPlayerGhost.vpMiniPlayer.offscreenPageLimit = 1
        binding.miniPlayerGhost.vpMiniPlayer.isUserInputEnabled = false

        binding.miniPlayerGhost.btnPlayPause.isEnabled = false
        binding.miniPlayerGhost.btnFollow.isEnabled = false
        binding.miniPlayerGhost.btnLike.isEnabled = false

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
                isCurrentUploaderFollowed = false

                binding.miniPlayer.root.visibility = View.GONE
                binding.miniPlayerGhost.root.visibility = View.GONE

                updateMiniPlayerLikeIcon()
                updateMiniPlayerFollowIcon(showButton = false)
                resetMiniPlayersPosition()

                return@observe
            }

            currentMiniSong = song

            isCurrentSongLiked = PlayerInteractionState.songState(song.id)?.liked ?: false
            isCurrentUploaderFollowed = PlayerInteractionState.artistState(song.uploaderId)
                ?.followed
                ?: false
            updateMiniPlayerLikeIcon()

            loadMiniPlayerLikeState(song)
            loadMiniPlayerFollowState(song)
            updateMiniPlayerVisibility()

            val index = PlayerManager.currentIndex.value ?: -1
            setMiniPlayerPage(index, smoothScroll = true)

            binding.miniPlayer.root.post {
                finishMiniTailAnimationIfNeeded()
            }
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
            binding.miniPlayerGhost.btnPlayPause.setImageResource(icon)

            updateMiniPlayerVisibility()
        }

        binding.miniPlayer.btnPlayPause.setOnClickListener {
            PlayerManager.toggle()
        }

        binding.miniPlayer.btnLike.setOnClickListener {
            toggleMiniPlayerLike()
        }

        binding.miniPlayer.btnFollow.setOnClickListener {
            toggleMiniPlayerFollow()
        }

        PlayerInteractionState.songLikeUpdates.observe(this) { state ->
            if (currentMiniSong?.id != state.songId) return@observe

            isCurrentSongLiked = state.liked
            updateMiniPlayerLikeIcon()
        }

        PlayerInteractionState.artistFollowUpdates.observe(this) { state ->
            if (currentMiniSong?.uploaderId != state.userId) return@observe

            isCurrentUploaderFollowed = state.followed
            updateMiniPlayerFollowIcon(showButton = true)
        }
    }

    private fun dragMiniPlayer(diffX: Float) {
        val miniRoot = binding.miniPlayer.root
        val width = miniRoot.width.takeIf { it > 0 } ?: return

        miniRoot.animate().cancel()

        miniRoot.translationX = diffX
        miniRoot.alpha = (1f - abs(diffX) / width)
            .coerceIn(0.45f, 1f)
    }

    private fun animateMiniPlayerOutThenChange(direction: Int) {
        val miniRoot = binding.miniPlayer.root
        val width = miniRoot.width.takeIf { it > 0 } ?: return

        pendingMiniEnterDirection = direction

        miniRoot.animate()
            .translationX(direction * width.toFloat())
            .alpha(0f)
            .setDuration(160L)
            .withEndAction {
                if (direction < 0) {
                    PlayerManager.playNext()
                } else {
                    PlayerManager.playPrevious()
                }

                /**
                 * Nếu PlayerManager không đổi bài được,
                 * ví dụ fallback rỗng, thì kéo mini player về lại.
                 */
                miniRoot.postDelayed({
                    if (pendingMiniEnterDirection == direction) {
                        pendingMiniEnterDirection = 0
                        resetMiniPlayerPosition()
                    }
                }, 700L)
            }
            .start()
    }

    private fun animateMiniPlayerInIfNeeded() {
        val miniRoot = binding.miniPlayer.root
        val width = miniRoot.width.takeIf { it > 0 } ?: return

        val direction = pendingMiniEnterDirection

        if (direction == 0) {
            resetMiniPlayerPosition()
            return
        }

        pendingMiniEnterDirection = 0

        miniRoot.animate().cancel()
        miniRoot.translationX = -direction * width.toFloat()
        miniRoot.alpha = 0f

        miniRoot.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(180L)
            .start()
    }

    private fun resetMiniPlayerPosition() {
        binding.miniPlayer.root.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(120L)
            .start()
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

    private fun loadMiniPlayerLikeState(song: Song) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (soundCloudSocialRepository.isSoundCloudSong(song)) {
                    val social = soundCloudSocialRepository.getTrackSocial(song.id)

                    PlayerInteractionState.publishSongLike(
                        SongLikeState(
                            songId = song.id,
                            liked = social.liked,
                            likesCount = social.likesCount,
                            commentsCount = social.commentsCount
                        )
                    )
                } else {
                    val liked = songRepository.isSongLiked(song.id)
                    val cached = PlayerInteractionState.songState(song.id)

                    PlayerInteractionState.publishSongLike(
                        SongLikeState(
                            songId = song.id,
                            liked = liked,
                            likesCount = cached?.likesCount ?: song.likes,
                            commentsCount = cached?.commentsCount ?: song.commentsCount
                        )
                    )
                }
            } catch (_: Exception) { /* Preserve the last known state on a transient error. */ }
        }
    }

    private fun toggleMiniPlayerLike() {
        val song = currentMiniSong ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (soundCloudSocialRepository.isSoundCloudSong(song)) {
                    val social = soundCloudSocialRepository.toggleTrackLike(song.id)

                    PlayerInteractionState.publishSongLike(
                        SongLikeState(
                            songId = song.id,
                            liked = social.liked,
                            likesCount = social.likesCount,
                            commentsCount = social.commentsCount
                        )
                    )

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            if (social.liked) getString(R.string.added_to_your_likes)
                            else getString(R.string.removed_from_your_likes),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    val liked = songRepository.toggleLikeSong(song)
                    val currentCount = PlayerInteractionState.songState(song.id)
                        ?.likesCount
                        ?: song.likes
                    val newCount = if (liked) currentCount + 1
                    else (currentCount - 1).coerceAtLeast(0)

                    PlayerInteractionState.publishSongLike(
                        SongLikeState(
                            songId = song.id,
                            liked = liked,
                            likesCount = newCount,
                            commentsCount = PlayerInteractionState.songState(song.id)
                                ?.commentsCount
                                ?: song.commentsCount
                        )
                    )

                    withContext(Dispatchers.Main) {
                        val message = if (liked) {
                            getString(R.string.added_to_your_likes)
                        } else {
                            getString(R.string.removed_from_your_likes)
                        }
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    }
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

    private fun loadMiniPlayerFollowState(song: Song) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val uploaderId = song.uploaderId

        if (uploaderId.isBlank() || currentUserId == uploaderId) {
            isCurrentUploaderFollowed = false
            updateMiniPlayerFollowIcon(showButton = false)
            return
        }

        updateMiniPlayerFollowIcon(showButton = true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val followed = songRepository.isFollowing(uploaderId)
                val followerCount = songRepository.getFollowerCount(uploaderId)

                PlayerInteractionState.publishArtistFollow(
                    ArtistFollowState(
                        userId = uploaderId,
                        followed = followed,
                        followerCount = followerCount
                    )
                )
            } catch (_: Exception) { /* Preserve the last known state on a transient error. */ }
        }
    }

    private fun toggleMiniPlayerFollow() {
        val song = currentMiniSong ?: return
        val uploaderId = song.uploaderId
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        if (uploaderId.isBlank()) {
            Toast.makeText(
                this,
                getString(R.string.target_user_not_found),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (currentUserId == uploaderId) {
            Toast.makeText(
                this,
                getString(R.string.cannot_follow_yourself),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val followed = songRepository.toggleFollowUser(uploaderId)
                val followerCount = songRepository.getFollowerCount(uploaderId)

                PlayerInteractionState.publishArtistFollow(
                    ArtistFollowState(
                        userId = uploaderId,
                        followed = followed,
                        followerCount = followerCount
                    )
                )

                withContext(Dispatchers.Main) {
                    val message = if (followed) {
                        getString(R.string.followed_successfully)
                    } else {
                        getString(R.string.unfollowed_successfully)
                    }

                    Toast.makeText(
                        this@MainActivity,
                        message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        e.message ?: getString(R.string.follow_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun updateMiniPlayerFollowIcon(showButton: Boolean = true) {
        binding.miniPlayer.btnFollow.visibility =
            if (showButton) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.miniPlayer.btnFollow.alpha =
            if (isCurrentUploaderFollowed) {
                1f
            } else {
                0.4f
            }

        binding.miniPlayer.btnFollow.setImageResource(
            if (isCurrentUploaderFollowed) R.drawable.ic_followed else R.drawable.ic_follow
        )
        binding.miniPlayerGhost.btnFollow.setImageResource(
            if (isCurrentUploaderFollowed) R.drawable.ic_followed else R.drawable.ic_follow
        )
    }

    private fun updateMiniPlayerLikeIcon() {
        binding.miniPlayer.btnLike.alpha =
            if (isCurrentSongLiked) {
                1f
            } else {
                0.4f
            }

        binding.miniPlayer.btnLike.setImageResource(
            if (isCurrentSongLiked) R.drawable.ic_liked else R.drawable.ic_like
        )
        binding.miniPlayerGhost.btnLike.setImageResource(
            if (isCurrentSongLiked) R.drawable.ic_liked else R.drawable.ic_like
        )
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
    private fun dragMiniPlayersTogether(diffX: Float) {
        val miniRoot = binding.miniPlayer.root
        val ghostRoot = binding.miniPlayerGhost.root
        val width = miniRoot.width.takeIf { it > 0 } ?: return

        val direction = if (diffX < 0) -1 else 1
        val targetSong = prepareGhostMiniPlayer(direction) ?: run {
            miniRoot.translationX = diffX * 0.25f
            return
        }

        miniRoot.animate().cancel()
        ghostRoot.animate().cancel()

        ghostRoot.visibility = View.VISIBLE

        miniRoot.translationX = diffX
        miniRoot.alpha = (1f - kotlin.math.abs(diffX) / width)
            .coerceIn(0.45f, 1f)

        ghostRoot.translationX = (-direction * width.toFloat()) + diffX
        ghostRoot.alpha = (kotlin.math.abs(diffX) / width)
            .coerceIn(0.45f, 1f)
    }

    private fun prepareGhostMiniPlayer(direction: Int): Song? {
        if (
            pendingMiniEnterDirection == direction &&
            pendingMiniTargetSong != null
        ) {
            return pendingMiniTargetSong
        }

        val target = findMiniPlayerTargetSong(direction) ?: return null

        pendingMiniEnterDirection = direction
        pendingMiniTargetSong = target.first
        pendingMiniTargetFromPlaylist = target.second

        ghostMiniPlayerPagerAdapter.submitList(listOf(target.first)) {
            binding.miniPlayerGhost.vpMiniPlayer.setCurrentItem(0, false)
        }

        val playIcon = if (PlayerManager.isCurrentlyPlaying()) {
            R.drawable.ic_pause
        } else {
            R.drawable.ic_play_button_circle
        }

        binding.miniPlayerGhost.btnPlayPause.setImageResource(playIcon)
        binding.miniPlayerGhost.btnLike.alpha = 0.4f
        binding.miniPlayerGhost.btnFollow.alpha = 0.4f

        binding.miniPlayerGhost.root.visibility = View.VISIBLE

        return target.first
    }

    private fun findMiniPlayerTargetSong(direction: Int): Pair<Song, Boolean>? {
        val playlist = PlayerManager.playlistSongs.value.orEmpty()
        val currentIndex = PlayerManager.currentIndex.value ?: -1

        if (playlist.size > 1 && currentIndex in playlist.indices) {
            val targetIndex =
                if (direction < 0) {
                    if (currentIndex + 1 < playlist.size) {
                        currentIndex + 1
                    } else {
                        0
                    }
                } else {
                    if (currentIndex - 1 >= 0) {
                        currentIndex - 1
                    } else {
                        playlist.lastIndex
                    }
                }

            val targetSong = playlist.getOrNull(targetIndex) ?: return null
            return targetSong to true
        }

        val currentId = PlayerManager.currentSong.value?.id

        val fallbackSongs = PlayerManager.fallbackSongs.value
            .orEmpty()
            .filter { it.id != currentId }

        val targetSong = fallbackSongs.randomOrNull() ?: return null

        return targetSong to false
    }

    private fun animateMiniPlayersOutThenChange(direction: Int) {
        val miniRoot = binding.miniPlayer.root
        val ghostRoot = binding.miniPlayerGhost.root
        val width = miniRoot.width.takeIf { it > 0 } ?: return

        val targetSong = prepareGhostMiniPlayer(direction)

        if (targetSong == null) {
            resetMiniPlayersPosition()
            return
        }

        isMiniTailAnimating = true

        miniRoot.animate().cancel()
        ghostRoot.animate().cancel()

        ghostRoot.visibility = View.VISIBLE

        miniRoot.animate()
            .translationX(direction * width.toFloat())
            .alpha(0f)
            .setDuration(160L)
            .start()

        ghostRoot.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(160L)
            .withEndAction {
                if (pendingMiniTargetFromPlaylist) {
                    if (direction < 0) {
                        PlayerManager.playNext()
                    } else {
                        PlayerManager.playPrevious()
                    }
                } else {
                    PlayerManager.playPreparedSong(targetSong)
                }

                ghostRoot.postDelayed({
                    if (isMiniTailAnimating) {
                        isMiniTailAnimating = false
                        pendingMiniEnterDirection = 0
                        pendingMiniTargetSong = null
                        pendingMiniTargetFromPlaylist = false
                        resetMiniPlayersPosition()
                    }
                }, 1000L)
            }
            .start()
    }

    private fun finishMiniTailAnimationIfNeeded() {
        if (!isMiniTailAnimating) {
            resetMiniPlayersPosition()
            return
        }

        isMiniTailAnimating = false
        pendingMiniEnterDirection = 0
        pendingMiniTargetSong = null
        pendingMiniTargetFromPlaylist = false

        binding.miniPlayer.root.animate().cancel()
        binding.miniPlayerGhost.root.animate().cancel()

        binding.miniPlayer.root.translationX = 0f
        binding.miniPlayer.root.alpha = 1f

        binding.miniPlayerGhost.root.visibility = View.GONE
        binding.miniPlayerGhost.root.translationX = 0f
        binding.miniPlayerGhost.root.alpha = 1f
    }

    private fun resetMiniPlayersPosition() {
        binding.miniPlayer.root.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(120L)
            .start()

        binding.miniPlayerGhost.root.animate()
            .alpha(0f)
            .setDuration(120L)
            .withEndAction {
                binding.miniPlayerGhost.root.visibility = View.GONE
                binding.miniPlayerGhost.root.translationX = 0f
                binding.miniPlayerGhost.root.alpha = 1f
            }
            .start()
    }

}
