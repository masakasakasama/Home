package com.masakasakasama.home.stock

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Quote(val symbol: String, val price: Double, val changePct: Double)

/**
 * Reads the watchlist that the Stock widget exposes through its
 * signature-protected ContentProvider, then fetches the same Yahoo
 * Finance data the widget itself uses.
 */
object StockLive {

    private val WATCHLIST: Uri =
        Uri.parse("content://com.example.stockwidget.watchlist/symbols")

    private fun readWatchlist(context: Context): List<String> = runCatching {
        context.contentResolver.query(WATCHLIST, null, null, null, null)
            ?.use { c ->
                val out = mutableListOf<String>()
                val idx = c.getColumnIndex("symbol")
                if (idx < 0) return emptyList()
                while (c.moveToNext()) c.getString(idx)?.let { out += it }
                out
            } ?: emptyList()
    }.getOrDefault(emptyList())

    private fun fetchQuote(symbol: String): Quote? = runCatching {
        val url = URL(
            "https://query1.finance.yahoo.com/v8/finance/chart/" +
                Uri.encode(symbol) + "?range=1d&interval=1d"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        conn.inputStream.bufferedReader().use { it.readText() }.let { body ->
            val meta = JSONObject(body)
                .getJSONObject("chart")
                .getJSONArray("result")
                .getJSONObject(0)
                .getJSONObject("meta")
            val price = meta.getDouble("regularMarketPrice")
            val prev = when {
                meta.has("chartPreviousClose") -> meta.getDouble("chartPreviousClose")
                meta.has("previousClose") -> meta.getDouble("previousClose")
                else -> price
            }
            val pct = if (prev != 0.0) (price - prev) / prev * 100.0 else 0.0
            Quote(symbol, price, pct)
        }
    }.getOrNull()

    /** Up-to-date quotes for the widget's watchlist, or empty when unavailable. */
    suspend fun quotes(context: Context): List<Quote> = withContext(Dispatchers.IO) {
        readWatchlist(context).take(6).mapNotNull { fetchQuote(it) }
    }

    private val LABELS = mapOf(
        "USDJPY=X" to "ドル円",
        "EURJPY=X" to "ユーロ円",
        "^N225" to "日経平均",
        "^GSPC" to "S&P 500",
        "^DJI" to "ダウ",
        "^IXIC" to "NASDAQ",
        "GC=F" to "金",
        "CL=F" to "原油",
        "BTC-JPY" to "ビットコイン",
        "BTC-USD" to "Bitcoin",
        "7203.T" to "トヨタ",
    )

    /** Friendly display name for a ticker, falling back to the symbol. */
    fun label(symbol: String): String = LABELS[symbol] ?: symbol

    /** Price formatted for display (thousands separators, sane decimals). */
    fun formatPrice(p: Double): String =
        if (p >= 1000) "%,.0f".format(p) else "%,.2f".format(p)
}
