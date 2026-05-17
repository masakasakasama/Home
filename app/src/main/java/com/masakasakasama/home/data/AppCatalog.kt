package com.masakasakasama.home.data

import androidx.compose.ui.graphics.Color

/** Where a tile sends the user when tapped. */
sealed interface Target {
    /** Open this URL in the browser (GitHub Pages web apps). */
    data class Web(val url: String) : Target

    /** Launch an already-installed app by its applicationId. */
    data class InstalledApp(val packageName: String) : Target
}

data class AppEntry(
    val title: String,
    val emoji: String,
    val color: Color,
    val target: Target,
)

object AppCatalog {

    /** Home updates itself from this repo's GitHub Releases. */
    const val SELF_OWNER = "masakasakasama"
    const val SELF_REPO = "Home"

    private fun pages(repo: String) =
        Target.Web("https://masakasakasama.github.io/$repo/")

    val apps = listOf(
        AppEntry("フィットネス", "💪", Color(0xFFE53935), pages("Fitness")),
        AppEntry("英語ニュース", "📰", Color(0xFF1E88E5), Target.Web("https://english-news-app-eight.vercel.app")),
        AppEntry("株", "📈", Color(0xFF43A047), Target.InstalledApp("com.masakasakasama.stock")),
        AppEntry("語学学習", "🗣️", Color(0xFF8E24AA), pages("Language_learning")),
        AppEntry("割り勘", "💴", Color(0xFFF4511E), pages("warikan")),
        AppEntry("タスク管理", "✅", Color(0xFF00897B), pages("Task_management")),
    )
}
