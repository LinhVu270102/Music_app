package com.example.music_app.ui.common

import android.widget.TextView

/**
 * Keeps long song titles readable in compact UI areas.
 *
 * The original title remains untouched in the data layer; only the displayed
 * text is shortened and the full title is kept as the view contentDescription.
 */
object SongTitleDisplay {
    const val MINI_PLAYER_MAX_LENGTH = 24
    const val TRENDING_GENRE_MAX_LENGTH = 10
    const val LIST_MAX_LENGTH = 30
    const val PLAYER_MAX_LENGTH = 36
    const val DIALOG_MAX_LENGTH = 42
    const val ADMIN_MAX_LENGTH = 48
}

fun String.toShortSongTitle(maxLength: Int = SongTitleDisplay.LIST_MAX_LENGTH): String {
    val normalizedTitle = trim().replace(MULTIPLE_WHITESPACE, " ")
    if (normalizedTitle.length <= maxLength) return normalizedTitle

    val safeMaxLength = maxLength.coerceAtLeast(ELLIPSIS.length + 1)
    val visibleLength = safeMaxLength - ELLIPSIS.length
    val visibleTitle = normalizedTitle
        .take(visibleLength)
        .trimEnd(' ', '-', '–', '|', '/', '(')

    return "$visibleTitle$ELLIPSIS"
}

fun TextView.setShortSongTitle(
    title: String,
    maxLength: Int = SongTitleDisplay.LIST_MAX_LENGTH
) {
    text = title.toShortSongTitle(maxLength)
    contentDescription = title
}

private const val ELLIPSIS = "..."
private val MULTIPLE_WHITESPACE = Regex("\\s+")
