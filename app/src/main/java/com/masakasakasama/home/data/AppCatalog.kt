package com.masakasakasama.home.data

import androidx.compose.ui.graphics.Color

/**
 * One launchable app in the Home grid.
 *
 * @param packageName Android applicationId of the target app. When the app is
 *   installed Home launches it directly. Leave null until you know it; the
 *   tile then always goes through the install/update flow instead.
 */
data class AppEntry(
    val title: String,
    val owner: String,
    val repo: String,
    val emoji: String,
    val color: Color,
    val packageName: String? = null,
)

object AppCatalog {

    const val SELF_OWNER = "masakasakasama"
    const val SELF_REPO = "Home"

    val apps = listOf(
        AppEntry("フィットネス", "masakasakasama", "Fitness", "💪", Color(0xFFE53935)),
        AppEntry("英語ニュース", "masakasakasama", "english-news-app", "📰", Color(0xFF1E88E5)),
        AppEntry("株", "masakasakasama", "Stock", "📈", Color(0xFF43A047)),
        AppEntry("語学学習", "masakasakasama", "Language_learning", "🗣️", Color(0xFF8E24AA)),
        AppEntry("割り勘", "masakasakasama", "warikan", "💴", Color(0xFFF4511E)),
        AppEntry("タスク管理", "masakasakasama", "Task_management", "✅", Color(0xFF00897B)),
    )
}
