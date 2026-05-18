package com.masakasakasama.home.tiles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class NewsFeed(val items: List<String>, val ageMinutes: Long?)

/** Top English headlines for the news widget. */
object NewsLive {

    private const val DEFAULT_FEED = "https://feeds.bbci.co.uk/news/world/rss.xml"

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
    private val PUBDATE = Regex("<pubDate>(.*?)</pubDate>", RegexOption.DOT_MATCHES_ALL)

    /** Up to [n] substantive headlines + how old the latest item is. */
    suspend fun feed(
        feedUrl: String = DEFAULT_FEED,
        n: Int = 3,
    ): NewsFeed = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            val xml = conn.inputStream.bufferedReader().use { it.readText() }
            val items = ITEM.findAll(xml)
                .mapNotNull { m -> TITLE.find(m.groupValues[1])?.groupValues?.get(1)?.trim() }
                .filter { it.isNotEmpty() && !FLUFF.containsMatchIn(it) }
                .distinct()
                .take(n)
                .toList()
            val age = PUBDATE.find(xml)?.groupValues?.get(1)?.trim()?.let { raw ->
                runCatching {
                    val t = OffsetDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
                    ChronoUnit.MINUTES.between(t, OffsetDateTime.now()).coerceAtLeast(0)
                }.getOrNull()
            }
            NewsFeed(items, age)
        }.getOrDefault(NewsFeed(emptyList(), null))
    }

    /** "32 MIN AGO" style relative label, blank when unknown. */
    fun ago(minutes: Long?): String = when {
        minutes == null -> ""
        minutes < 1 -> "JUST NOW"
        minutes < 60 -> "$minutes MIN AGO"
        minutes < 1440 -> "${minutes / 60} HR AGO"
        else -> "${minutes / 1440} D AGO"
    }
}

/**
 * Date-based "when to train next" hint. Training days are passed in as
 * DayOfWeek values (1=Mon .. 7=Sun) so they can come from user settings.
 */
object FitnessTip {

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

    private fun isTrain(d: DayOfWeek, days: Set<Int>) = d.value in days

    /** Compact label for the dashboard, e.g. "今日" / "明日" / "金曜". */
    fun nextShort(days: Set<Int>, today: LocalDate = LocalDate.now()): String {
        if (days.isEmpty()) return "未設定"
        if (isTrain(today.dayOfWeek, days)) return "今日"
        var d = today
        repeat(7) {
            d = d.plusDays(1)
            if (isTrain(d.dayOfWeek, days))
                return if (d == today.plusDays(1)) "明日" else "${jp(d.dayOfWeek)}曜"
        }
        return "未定"
    }
}
