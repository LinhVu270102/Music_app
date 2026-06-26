package com.example.music_app.ui.common

import android.app.Dialog
import android.graphics.Color
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable

/** Applies the shared Orange Music dialog chrome after the window is available. */
fun Dialog.showCustomDialog() {
    show()
    window?.apply {
        setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
