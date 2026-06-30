package com.example.music_app.main

import android.view.View
import com.example.music_app.data.model.enums.FooterTab

/** Renders footer selection and forwards the selected tab to MainActivity. */
class FooterNavigationController(
    private val footer: View,
    private val homeButton: View,
    private val searchButton: View,
    private val libraryButton: View,
    private val profileButton: View,
    private val onTabSelected: (FooterTab) -> Unit
) {

    fun bind() {
        homeButton.setOnClickListener { onTabSelected(FooterTab.HOME) }
        searchButton.setOnClickListener { onTabSelected(FooterTab.SEARCH) }
        libraryButton.setOnClickListener { onTabSelected(FooterTab.LIBRARY) }
        profileButton.setOnClickListener { onTabSelected(FooterTab.PROFILE) }
    }

    fun select(tab: FooterTab) {
        buttons().forEach { button ->
            button.scaleX = DEFAULT_SCALE
            button.scaleY = DEFAULT_SCALE
            button.alpha = INACTIVE_ALPHA
        }

        buttonFor(tab).apply {
            scaleX = ACTIVE_SCALE
            scaleY = ACTIVE_SCALE
            alpha = ACTIVE_ALPHA
        }
    }

    fun setVisible(visible: Boolean) {
        footer.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun buttonFor(tab: FooterTab): View {
        return when (tab) {
            FooterTab.HOME -> homeButton
            FooterTab.SEARCH -> searchButton
            FooterTab.LIBRARY -> libraryButton
            FooterTab.PROFILE -> profileButton
        }
    }

    private fun buttons(): List<View> {
        return listOf(homeButton, searchButton, libraryButton, profileButton)
    }

    private companion object {
        const val DEFAULT_SCALE = 1f
        const val ACTIVE_SCALE = 1.2f
        const val INACTIVE_ALPHA = 0.5f
        const val ACTIVE_ALPHA = 1f
    }
}
