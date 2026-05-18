package com.masakasakasama.home.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.masakasakasama.home.MainActivity
import com.masakasakasama.home.R
import com.masakasakasama.home.data.Config
import com.masakasakasama.home.stock.StockLive
import com.masakasakasama.home.tiles.NewsLive
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        ids.forEach { id -> manager.updateAppWidget(id, buildViews(context)) }
        backgroundRefresh(context)
    }

    private fun backgroundRefresh(context: Context) {
        val app = context.applicationContext
        // Skip network when the app-written cache is still fresh. This keeps
        // the goAsync() window short and avoids redundant fetches.
        val fresh = System.currentTimeMillis() - Config.lastSync(app) <
            Config.refreshMin(app) * 60_000L
        if (fresh && Config.lastSync(app) > 0L) return
        val pending = goAsync()
        Thread {
            runCatching {
                runBlocking {
                    val quotes = StockLive.quotes(app, Config.watchlist(app))
                    if (quotes.isNotEmpty()) {
                        Config.cacheStock(app, quotes.map {
                            Triple(it.symbol, it.price, it.changePct)
                        })
                    }
                    val feed = NewsLive.feed(Config.newsFeed(app))
                    if (feed.items.isNotEmpty()) {
                        Config.cacheNews(app, feed.items, feed.ageMinutes)
                    }
                }
            }
            runCatching { pushUpdate(app) }
            pending.finish()
        }.start()
    }

    companion object {

        fun pushUpdate(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(
                ComponentName(app, HomeWidget::class.java)
            )
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(app)) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_home)

            val (stock, _) = Config.cachedStock(context)
            v.setTextViewText(
                R.id.widget_stock,
                if (stock.isEmpty()) "株価データなし"
                else stock.take(3).joinToString("   ") { (s, p, c) ->
                    val arrow = if (c >= 0) "▲" else "▼"
                    "${StockLive.label(s)} ${StockLive.formatPrice(p)} " +
                        "$arrow%.1f%%".format(kotlin.math.abs(c))
                }
            )

            val (news, _, _) = Config.cachedNews(context)
            v.setTextViewText(
                R.id.widget_news,
                news.firstOrNull() ?: "ニュースなし"
            )

            val sync = Config.lastSync(context)
            v.setTextViewText(
                R.id.widget_updated,
                if (sync == 0L) "—"
                else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sync))
            )

            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            return v
        }
    }
}
