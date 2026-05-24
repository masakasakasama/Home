package com.masakasakasama.home.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class TileKind { STOCK, NEWS, FITNESS, WEB }

data class Tile(
    val id: String,
    val title: String,
    val emoji: String,
    val colorArgb: Long,
    val category: String,
    val kind: TileKind,
    val url: String? = null,
    val pkg: String? = null,
) {
    /** github.io pages can publish a tiny status.json next to index. */
    val statusUrl: String?
        get() = if (kind == TileKind.WEB && url != null && url.endsWith("/"))
            url + "status.json" else null
}

/** Cached web-app metric, e.g. primary="4" label="OPEN" detail="今日 原稿". */
data class WebStatus(val primary: String, val label: String, val detail: String)

/**
 * Single source of truth for user-editable settings and the offline
 * cache. Everything is stored as JSON in SharedPreferences so the home
 * screen widget (a separate process entry point) can read the same data
 * without any network of its own.
 */
object Config {

    private const val FILE = "home_config"

    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- Built-in defaults --------------------------------------------

    private fun pages(repo: String) = "https://masakasakasama.github.io/$repo/"

    private val DEFAULTS: List<Tile> = listOf(
        Tile("stock", "株", "📈", 0xFF32D74B, "MARKETS", TileKind.STOCK,
            pkg = "com.example.stockwidget"),
        Tile("tasks", "タスク管理", "✅", 0xFF00897B, "TASKS", TileKind.WEB,
            url = pages("Task_management")),
        Tile("fitness", "フィットネス", "💪", 0xFFE53935, "TRAINING", TileKind.FITNESS,
            url = pages("Fitness")),
        Tile("news", "英語ニュース", "📰", 0xFF1E88E5, "BBC WORLD", TileKind.NEWS,
            url = "https://english-news-app-eight.vercel.app"),
        Tile("language", "語学学習", "🗣️", 0xFF8E24AA, "LANGUAGE", TileKind.WEB,
            url = pages("Language_learning")),
        Tile("split", "割り勘", "💴", 0xFFF4511E, "SPLIT", TileKind.WEB,
            url = pages("warikan")),
        Tile("marriage", "婚姻手続き", "💍", 0xFFD81B60, "PROCEDURE", TileKind.WEB,
            url = pages("Marriage_procedure")),
    )

    const val DEFAULT_NEWS_FEED = "https://feeds.bbci.co.uk/news/world/rss.xml"
    val DEFAULT_TRAIN_DAYS = setOf(1, 3, 5) // Mon, Wed, Fri (DayOfWeek value)
    const val DEFAULT_REFRESH_MIN = 30
    const val MIN_REFRESH_MIN = 15

    // ---- Settings -----------------------------------------------------

    fun newsFeed(c: Context): String =
        sp(c).getString("news_feed", null)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_NEWS_FEED

    fun setNewsFeed(c: Context, v: String) =
        sp(c).edit().putString("news_feed", v.trim()).apply()

    fun refreshMin(c: Context): Int =
        sp(c).getInt("refresh_min", DEFAULT_REFRESH_MIN)
            .coerceAtLeast(MIN_REFRESH_MIN)

    fun setRefreshMin(c: Context, v: Int) =
        sp(c).edit().putInt("refresh_min", v.coerceAtLeast(MIN_REFRESH_MIN)).apply()

    /** DayOfWeek values 1..7 that are training days. */
    fun trainDays(c: Context): Set<Int> {
        val raw = sp(c).getString("train_days", null) ?: return DEFAULT_TRAIN_DAYS
        val set = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }.toSet()
        return set.ifEmpty { DEFAULT_TRAIN_DAYS }
    }

    fun setTrainDays(c: Context, days: Set<Int>) =
        sp(c).edit().putString(
            "train_days",
            days.filter { it in 1..7 }.sorted().joinToString(",")
        ).apply()

    /** User watchlist override; empty list means "use the Stock app's". */
    fun watchlist(c: Context): List<String> =
        sp(c).getString("watchlist", null)
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: emptyList()

    fun setWatchlist(c: Context, symbols: List<String>) =
        sp(c).edit().putString(
            "watchlist",
            symbols.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")
        ).apply()

    // ---- Tiles (order / hidden / custom) ------------------------------

    private fun jsonArray(raw: String?): List<String> = runCatching {
        if (raw.isNullOrBlank()) emptyList()
        else JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) } }
    }.getOrDefault(emptyList())

    private fun customTiles(c: Context): List<Tile> = runCatching {
        val raw = sp(c).getString("custom_tiles", null) ?: return emptyList()
        val a = JSONArray(raw)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifBlank { return@mapNotNull null }
            val url = o.optString("url").ifBlank { return@mapNotNull null }
            Tile(
                id = id,
                title = o.optString("title").ifBlank { "リンク" },
                emoji = o.optString("emoji").ifBlank { "🔗" },
                colorArgb = 0xFF5E5CE6,
                category = o.optString("category").ifBlank { "LINK" },
                kind = TileKind.WEB,
                url = url,
            )
        }
    }.getOrDefault(emptyList())

    /** Effective, ordered, visible tile list for rendering. */
    fun tiles(c: Context): List<Tile> {
        val all = DEFAULTS + customTiles(c)
        val byId = all.associateBy { it.id }
        val hidden = jsonArray(sp(c).getString("tiles_hidden", null)).toSet()
        val order = jsonArray(sp(c).getString("tiles_order", null))
        val ordered = buildList {
            order.forEach { id -> byId[id]?.let { add(it) } }
            all.forEach { if (it.id !in order) add(it) }
        }
        return ordered.filter { it.id !in hidden }
    }

    /** Full list (including hidden) for the settings editor. */
    fun allTilesOrdered(c: Context): List<Tile> {
        val all = (DEFAULTS + customTiles(c)).associateBy { it.id }
        val order = jsonArray(sp(c).getString("tiles_order", null))
        return buildList {
            order.forEach { id -> all[id]?.let { add(it) } }
            (DEFAULTS + customTiles(c)).forEach { if (it.id !in order) add(it) }
        }
    }

    fun isHidden(c: Context, id: String): Boolean =
        id in jsonArray(sp(c).getString("tiles_hidden", null)).toSet()

    fun setHidden(c: Context, id: String, hidden: Boolean) {
        val cur = jsonArray(sp(c).getString("tiles_hidden", null)).toMutableSet()
        if (hidden) cur.add(id) else cur.remove(id)
        sp(c).edit().putString("tiles_hidden", JSONArray(cur.toList()).toString()).apply()
    }

    fun setOrder(c: Context, ids: List<String>) =
        sp(c).edit().putString("tiles_order", JSONArray(ids).toString()).apply()

    fun addCustomTile(c: Context, title: String, url: String) {
        val raw = sp(c).getString("custom_tiles", null)
        val arr = runCatching { if (raw.isNullOrBlank()) JSONArray() else JSONArray(raw) }
            .getOrDefault(JSONArray())
        arr.put(
            JSONObject()
                .put("id", "custom_" + System.currentTimeMillis())
                .put("title", title.trim().ifBlank { "リンク" })
                .put("url", url.trim())
                .put("category", "LINK")
                .put("emoji", "🔗")
        )
        sp(c).edit().putString("custom_tiles", arr.toString()).apply()
    }

    fun removeCustomTile(c: Context, id: String) {
        val raw = sp(c).getString("custom_tiles", null) ?: return
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return
        val kept = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("id") != id) kept.put(o)
        }
        sp(c).edit().putString("custom_tiles", kept.toString()).apply()
    }

    fun isCustom(id: String) = id.startsWith("custom_")

    // ---- Offline cache ------------------------------------------------

    private fun putCache(c: Context, key: String, payload: String) =
        sp(c).edit()
            .putString("cache_$key", payload)
            .putLong("cache_${key}_ts", System.currentTimeMillis())
            .apply()

    private fun cacheTs(c: Context, key: String): Long =
        sp(c).getLong("cache_${key}_ts", 0L)

    fun cacheStock(c: Context, quotes: List<Triple<String, Double, Double>>) {
        val a = JSONArray()
        quotes.forEach {
            a.put(JSONObject().put("s", it.first).put("p", it.second).put("c", it.third))
        }
        putCache(c, "stock", a.toString())
    }

    /** Cached quotes as (symbol, price, changePct) + age in millis. */
    fun cachedStock(c: Context): Pair<List<Triple<String, Double, Double>>, Long> {
        val raw = sp(c).getString("cache_stock", null) ?: return emptyList<Triple<String, Double, Double>>() to 0L
        val list = runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Triple(o.getString("s"), o.getDouble("p"), o.getDouble("c"))
            }
        }.getOrDefault(emptyList())
        return list to cacheTs(c, "stock")
    }

    fun cacheNews(c: Context, items: List<String>, ageMinutes: Long?) {
        val o = JSONObject()
        o.put("items", JSONArray(items))
        if (ageMinutes != null) o.put("age", ageMinutes)
        putCache(c, "news", o.toString())
    }

    /** Cached headlines, original feed age (min) or null, cache age (ms). */
    fun cachedNews(c: Context): Triple<List<String>, Long?, Long> {
        val raw = sp(c).getString("cache_news", null)
            ?: return Triple(emptyList(), null, 0L)
        val res = runCatching {
            val o = JSONObject(raw)
            val arr = o.optJSONArray("items") ?: JSONArray()
            val items = (0 until arr.length()).map { arr.getString(it) }
            val age = if (o.has("age")) o.getLong("age") else null
            items to age
        }.getOrDefault(emptyList<String>() to null)
        return Triple(res.first, res.second, cacheTs(c, "news"))
    }

    fun cacheStatus(c: Context, id: String, s: WebStatus) {
        putCache(
            c, "status_$id",
            JSONObject()
                .put("primary", s.primary)
                .put("label", s.label)
                .put("detail", s.detail)
                .toString()
        )
    }

    fun cachedStatus(c: Context, id: String): WebStatus? {
        val raw = sp(c).getString("cache_status_$id", null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            WebStatus(o.optString("primary"), o.optString("label"), o.optString("detail"))
        }.getOrNull()
    }

    /** Most recent successful refresh across stock + news, 0 when none. */
    fun lastSync(c: Context): Long =
        maxOf(cacheTs(c, "stock"), cacheTs(c, "news"))
}
