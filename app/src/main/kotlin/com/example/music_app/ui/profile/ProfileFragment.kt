package com.example.music_app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.User
import com.example.music_app.databinding.FragmentProfileBinding
import com.example.music_app.ui.player.PlaybackLauncher
import com.example.music_app.ui.song.SongAdapter

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    private lateinit var adapter: SongAdapter
    private var currentSongs: List<Song> = emptyList()

    private var targetUserId: String = ""

    companion object {
        private const val ARG_USER_ID = "userId"

        fun newInstance(userId: String): ProfileFragment {
            return ProfileFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_USER_ID, userId)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        targetUserId = arguments?.getString(ARG_USER_ID).orEmpty()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()

        viewModel.loadProfile(targetUserId)
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadProfile(targetUserId)
    }

    private fun setupClickListeners() {
        binding.btnEditProfile.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, EditProfileFragment())
                .addToBackStack(null)
                .commit()
        }
        binding.btnFollowProfile.setOnClickListener {
            viewModel.toggleFollow()
        }
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter(
            onItemClick = { song ->
                playSong(song)
            }
        )

        binding.profileMusicList.layoutManager = LinearLayoutManager(requireContext())
        binding.profileMusicList.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.user.observe(viewLifecycleOwner) { user ->
            bindUser(user)
        }

        viewModel.mySongs.observe(viewLifecycleOwner) { songs ->
            currentSongs = songs
            adapter.setData(songs)

            binding.songCount.text =
                getString(R.string.song_count_format, songs.size)

            binding.myMusicLabel.text =
                getString(R.string.my_uploaded_music_count, songs.size)

            binding.profileMusicList.isVisible = songs.isNotEmpty()
            binding.tvNoUploadedSongs.isVisible = songs.isEmpty()
        }

        viewModel.myPlaylists.observe(viewLifecycleOwner) { playlists ->
            binding.playlistCount.text =
                getString(R.string.playlist_count_format, playlists.size)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.isVisible = isLoading
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                Toast.makeText(
                    requireContext(),
                    getString(it),
                    Toast.LENGTH_SHORT
                ).show()

                viewModel.clearErrorMessage()
            }
        }
        viewModel.isOwnProfile.observe(viewLifecycleOwner) { isOwnProfile ->
            binding.btnEditProfile.isVisible = isOwnProfile
            binding.btnFollowProfile.isVisible = !isOwnProfile
        }

        viewModel.isFollowing.observe(viewLifecycleOwner) { isFollowing ->
            binding.btnFollowProfile.setImageResource(
                if (isFollowing) R.drawable.ic_followed else R.drawable.ic_follow
            )
            binding.btnFollowProfile.contentDescription = getString(
                if (isFollowing) R.string.following else R.string.follow
            )
            binding.btnFollowProfile.alpha = if (isFollowing) 1f else 0.55f
        }
    }

    private fun bindUser(user: User?) {
        if (user == null) {
            bindGuest()
            return
        }

        binding.profileName.text = getDisplayName(user)

        binding.profileUsername.text =
            if (user.username.isNotBlank()) {
                getString(R.string.username_format, user.username)
            } else {
                getString(R.string.username_not_updated)
            }

        binding.profileEmail.text = user.email.ifBlank {
            getString(R.string.email_not_updated)
        }

        binding.profileBio.text = user.bio.ifBlank {
            getString(R.string.default_bio)
        }

        binding.likedSongsCount.text =
            getString(R.string.liked_song_count_format, user.likedSongsCount)

        binding.followersCount.text =
            getString(R.string.followers_count_format, user.followersCount)

        binding.followingCount.text =
            getString(R.string.following_count_format, user.followingCount)

        binding.profileExtraInfo.text = buildExtraInfo(user)

        Glide.with(this)
            .load(user.avatarUrl.ifBlank { R.drawable.music_orange })
            .placeholder(R.drawable.music_orange)
            .error(R.drawable.music_orange)
            .circleCrop()
            .into(binding.profileAvatar)
    }

    private fun bindGuest() {
        binding.profileName.text = getString(R.string.guest)
        binding.profileUsername.text = getString(R.string.username_not_updated)
        binding.profileEmail.text = getString(R.string.not_logged_in)
        binding.profileBio.text = getString(R.string.default_bio)
        binding.profileExtraInfo.text = getString(R.string.profile_extra_info_placeholder)

        binding.songCount.text = getString(R.string.song_count_format, 0)
        binding.playlistCount.text = getString(R.string.playlist_count_format, 0)
        binding.likedSongsCount.text = getString(R.string.liked_song_count_format, 0)
        binding.followersCount.text = getString(R.string.followers_count_format, 0)
        binding.followingCount.text = getString(R.string.following_count_format, 0)

        Glide.with(this)
            .load(R.drawable.music_orange)
            .placeholder(R.drawable.music_orange)
            .error(R.drawable.music_orange)
            .circleCrop()
            .into(binding.profileAvatar)
    }

    private fun getDisplayName(user: User): String {
        return when {
            user.fullName.isNotBlank() -> user.fullName
            user.displayName.isNotBlank() -> user.displayName
            user.username.isNotBlank() -> user.username
            else -> getString(R.string.no_name)
        }
    }

    private fun buildExtraInfo(user: User): String {
        val favoriteGenresText = user.favoriteGenres
            .joinToString(", ")
            .ifBlank {
                getString(R.string.not_updated)
            }

        val moodTagsText = user.musicMoodTags
            .joinToString(", ")
            .ifBlank {
                getString(R.string.not_updated)
            }

        return getString(
            R.string.profile_extra_info_full_format,
            user.fullName.ifBlank { getString(R.string.not_updated) },
            user.phoneNumber.ifBlank { getString(R.string.not_updated) },
            user.gender.ifBlank { getString(R.string.not_updated) },
            user.country.ifBlank { getString(R.string.not_updated) },
            favoriteGenresText,
            moodTagsText
        )
    }

    private fun playSong(song: Song) {
        PlaybackLauncher.openPlayer(
            fragment = this,
            song = song,
            playlist = currentSongs
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
