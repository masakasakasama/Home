package com.masakasakasama.home.tiles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate

/** Top English headlines for the news widget. */
object NewsLive {

    const val SOURCE = "BBC World"
    private const val FEED = "https://feeds.bbci.co.uk/news/world/rss.xml"

    // Skip soft/celebrity/sport items so a slow news day shows nothing
    // rather than something しょうもない.
    private val FLUFF = Regex(
        "(?i)celebrity|gossip|royal|kardashian|football|soccer|" +
            "cricket|rugby|tennis|olympic|box office|red carpet|" +
            "fashion|horoscope|recipe|viral|tiktok"
    )

    private val ITEM = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
    private val TITLE = Regex(
        "<title>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</title>",
        RegexOption.DOT_MATCHES_ALL
    )

    /** Up to [n] substantive headlines, or empty when only fluff is available. */
    suspend fun headlines(n: Int = 3): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(FEED).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            val xml = conn.inputStream.bufferedReader().use { it.readText() }
            ITEM.findAll(xml)
                .mapNotNull { m -> TITLE.find(m.groupValues[1])?.groupValues?.get(1)?.trim() }
                .filter { it.isNotEmpty() && !FLUFF.containsMatchIn(it) }
                .distinct()
                .take(n)
                .toList()
        }.getOrDefault(emptyList())
    }
}

/** Date-based "when to train next" hint for the fitness widget. */
object FitnessTip {

    // Simple 3x/week split; no history source, so this is a steady nudge.
    val trainDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

    val week: List<DayOfWeek> = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
    )

    fun jp(d: DayOfWeek) = when (d) {
        DayOfWeek.MONDAY -> "月"
        DayOfWeek.TUESDAY -> "火"
        DayOfWeek.WEDNESDAY -> "水"
        DayOfWeek.THURSDAY -> "木"
        DayOfWeek.FRIDAY -> "金"
        DayOfWeek.SATURDAY -> "土"
        DayOfWeek.SUNDAY -> "日"
    }

    fun today(): DayOfWeek = LocalDate.now().dayOfWeek

    fun next(today: LocalDate = LocalDate.now()): String {
        if (today.dayOfWeek in trainDays) return "今日はトレーニング日 💪"
        var d = today
        repeat(7) {
            d = d.plusDays(1)
            if (d.dayOfWeek in trainDays) {
                val label = if (d == today.plusDays(1)) "明日" else "${jp(d.dayOfWeek)}曜"
                return "次は${label}がおすすめ"
            }
        }
        return "次のトレーニングを計画しよう"
    }
}
