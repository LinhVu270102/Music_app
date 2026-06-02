package com.example.music_app.ui.player

import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.FragmentPlayerBinding
import com.example.music_app.main.MainActivity
import com.example.music_app.player.PlayerManager
import java.util.concurrent.TimeUnit

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlayerViewModel by viewModels()
    private val handler = Handler(Looper.getMainLooper())

    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            val position = PlayerManager.getCurrentPosition()
            val duration = PlayerManager.getDuration()

            binding.playerSeekBar.max = duration.toInt()
            binding.playerSeekBar.progress = position.toInt()

            binding.playerCurrentTime.text = formatTime(position)
            binding.playerTotalTime.text = formatTime(duration)

            updatePlayPauseIcon()

            handler.postDelayed(this, 500)
        }
    }

    companion object {
        fun newInstance(songId: String): PlayerFragment {
            return PlayerFragment().apply {
                arguments = Bundle().apply {
                    putString("songId", songId)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? MainActivity)?.setMiniPlayerVisible(false)
        (requireActivity() as? MainActivity)?.setFooterVisible(false)

        setupActions()
        initObservers()

        updateShuffleIcon()
        updateLoopIcon()

        PlayerManager.currentSong.value?.let {
            updateUI(it)
            startProgressUpdater()
            return
        }

        val songId = arguments?.getString("songId")
        songId?.let {
            viewModel.loadSong(it)
        }
    }

    private fun setupActions() {
        binding.btnPlayPause.setOnClickListener {
            PlayerManager.toggle()
            updatePlayPauseIcon()
        }

        binding.btnReturn.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnShuffle.setOnClickListener {
            PlayerManager.toggleShuffle()
        }

        binding.btnLoop.setOnClickListener {
            PlayerManager.toggleLoopMode()
        }

        binding.btnNext.setOnClickListener {
            PlayerManager.playNext()
            updatePlayPauseIcon()
        }

        binding.btnPrev.setOnClickListener {
            PlayerManager.playPrevious()
            updatePlayPauseIcon()
        }

        binding.playerSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        PlayerManager.seekTo(progress.toLong())
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )
    }

    private fun initObservers() {
        viewModel.song.observe(viewLifecycleOwner) { song ->
            song?.let {
                updateUI(it)
                PlayerManager.play(it)
                startProgressUpdater()
            }
        }

        PlayerManager.currentSong.observe(viewLifecycleOwner) { song ->
            song?.let {
                updateUI(it)
                startProgressUpdater()
            }
        }

        PlayerManager.isShuffleEnabled.observe(viewLifecycleOwner) {
            updateShuffleIcon()
        }

        PlayerManager.loopMode.observe(viewLifecycleOwner) {
            updateLoopIcon()
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                showToast(it)
            }
        }
    }

    private fun updateUI(song: Song) {
        binding.playerSongTitle.text = song.title
        binding.playerArtist.text = song.artist

        Glide.with(this)
            .asBitmap()
            .load(song.coverUrl)
            .placeholder(R.drawable.music_orange)
            .into(object : com.bumptech.glide.request.target.CustomTarget<Bitmap>() {

                override fun onResourceReady(
                    resource: Bitmap,
                    transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?
                ) {
                    binding.playerCover.setImageBitmap(resource)

                    Palette.from(resource).generate { palette ->
                        val dominantColor = palette?.getDominantColor(
                            requireContext().getColor(R.color.black)
                        ) ?: requireContext().getColor(R.color.black)

                        applyGradientBackground(dominantColor)
                    }
                }

                override fun onLoadCleared(
                    placeholder: android.graphics.drawable.Drawable?
                ) {
                }
            })

        updatePlayPauseIcon()
    }

    private fun applyGradientBackground(color: Int) {
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                color,
                requireContext().getColor(R.color.black)
            )
        )

        gradient.cornerRadius = 0f
        binding.playerRoot.background = gradient
    }

    private fun updatePlayPauseIcon() {
        val icon = if (PlayerManager.isCurrentlyPlaying()) {
            R.drawable.ic_pause
        } else {
            R.drawable.ic_play
        }

        binding.btnPlayPause.setImageResource(icon)
    }

    private fun updateShuffleIcon() {
        val enabled = PlayerManager.isShuffleEnabled.value == true

        binding.btnShuffle.alpha = if (enabled) {
            1f
        } else {
            0.4f
        }
    }

    private fun updateLoopIcon() {
        when (PlayerManager.loopMode.value) {
            PlayerManager.LoopMode.OFF -> {
                binding.btnLoop.setImageResource(R.drawable.ic_loop)
                binding.btnLoop.alpha = 0.4f
            }

            PlayerManager.LoopMode.PLAYLIST -> {
                binding.btnLoop.setImageResource(R.drawable.ic_loop)
                binding.btnLoop.alpha = 1f
            }

            PlayerManager.LoopMode.ONE -> {
                binding.btnLoop.setImageResource(R.drawable.ic_loop1)
                binding.btnLoop.alpha = 1f
            }

            null -> {
                binding.btnLoop.setImageResource(R.drawable.ic_loop)
                binding.btnLoop.alpha = 0.4f
            }
        }
    }

    private fun startProgressUpdater() {
        handler.removeCallbacks(updateSeekBarRunnable)
        handler.post(updateSeekBarRunnable)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateSeekBarRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        handler.removeCallbacks(updateSeekBarRunnable)

        (requireActivity() as? MainActivity)?.setMiniPlayerVisible(true)
        (requireActivity() as? MainActivity)?.setFooterVisible(true)

        _binding = null
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun formatTime(ms: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}