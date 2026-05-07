package com.example.music_app.main

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.music_app.R
import com.example.music_app.databinding.ActivityMainBinding
import com.example.music_app.player.PlayerManager
import com.example.music_app.service.MusicService
import com.example.music_app.ui.auth.LoginActivity
import com.example.music_app.ui.home.HomeFragment
import com.example.music_app.ui.library.LibraryFragment
import com.example.music_app.ui.player.PlayerFragment
import com.example.music_app.ui.profile.ProfileFragment
import com.example.music_app.ui.search.SearchFragment
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    enum class FooterTab {
        HOME, SEARCH, LIBRARY, PROFILE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        PlayerManager.init(this)
        startMusicService()

        setupFooter()
        setupMiniPlayer()

        if (savedInstanceState == null) {
            openFragment(HomeFragment())
            highlightFooter(FooterTab.HOME)
        }
    }

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
            openFragment(HomeFragment())
            highlightFooter(FooterTab.HOME)
        }

        binding.btnSearch.setOnClickListener {
            openFragment(SearchFragment())
            highlightFooter(FooterTab.SEARCH)
        }

        binding.btnLibrary.setOnClickListener {
            openFragment(LibraryFragment())
            highlightFooter(FooterTab.LIBRARY)
        }

        binding.btnProfile.setOnClickListener {
            openFragment(ProfileFragment())
            highlightFooter(FooterTab.PROFILE)
        }
    }

    private fun setupMiniPlayer() {
        binding.miniPlayer.root.visibility = View.GONE

        PlayerManager.currentSong.observe(this) { song ->
            if (song == null) {
                binding.miniPlayer.root.visibility = View.GONE
                return@observe
            }

            binding.miniPlayer.trackTitle.text = song.title
            binding.miniPlayer.trackArtist.text = song.artist

            val isPlayerScreen =
                supportFragmentManager.findFragmentById(binding.fragmentContainer.id) is PlayerFragment

            binding.miniPlayer.root.visibility =
                if (isPlayerScreen) View.GONE else View.VISIBLE

            binding.miniPlayer.root.setOnClickListener {
                openFragment(PlayerFragment.newInstance(song.id))
                setMiniPlayerVisible(false)
                setFooterVisible(false)
            }
        }

        PlayerManager.isPlaying.observe(this) { isPlaying ->
            val icon = if (isPlaying) {
                R.drawable.ic_pause
            } else {
                R.drawable.ic_play_button_circle
            }

            binding.miniPlayer.btnPlayPause.setImageResource(icon)
        }

        binding.miniPlayer.btnPlayPause.setOnClickListener {
            PlayerManager.toggle()
        }
    }

    fun setMiniPlayerVisible(visible: Boolean) {
        val hasSong = PlayerManager.currentSong.value != null

        binding.miniPlayer.root.visibility =
            if (visible && hasSong) View.VISIBLE else View.GONE
    }

    fun setFooterVisible(visible: Boolean) {
        binding.appFooter.visibility =
            if (visible) View.VISIBLE else View.GONE
    }

    private fun openFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, fragment)
            .addToBackStack(null)
            .commit()
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