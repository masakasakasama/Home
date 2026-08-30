package com.masakasakasama.home.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.masakasakasama.home.FocusActivity
import com.masakasakasama.home.R
import com.masakasakasama.home.focus.PriorityEngine
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
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_COMPLETE) {
            intent.getStringExtra(EXTRA_FOCUS_ID)?.let { focusId ->
                PriorityEngine.complete(context, focusId)
                pushUpdate(context)
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_COMPLETE = "com.masakasakasama.home.widget.COMPLETE_FOCUS"
        private const val EXTRA_FOCUS_ID = "focus_id"

        fun pushUpdate(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, HomeWidget::class.java))
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(app)) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_home)
            val focus = PriorityEngine.snapshot(context)

            v.setTextViewText(R.id.widget_focus_title, "${focus.now.emoji} ${focus.now.title}")
            v.setTextViewText(R.id.widget_focus_detail, focus.now.detail)
            v.setTextViewText(R.id.widget_progress, focus.summary)
            v.setTextViewText(R.id.widget_action, focus.now.actionLabel)
            v.setTextViewText(
                R.id.widget_next,
                "NEXT  ${focus.next.emoji} ${focus.next.title}  ·  ${focus.next.detail}",
            )
            v.setTextViewText(
                R.id.widget_updated,
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            )

            val complete = PendingIntent.getBroadcast(
                context,
                1000 + focus.now.id.hashCode(),
                Intent(context, HomeWidget::class.java)
                    .setAction(ACTION_COMPLETE)
                    .putExtra(EXTRA_FOCUS_ID, focus.now.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.widget_action, complete)

            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, FocusActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            return v
        }
    }
}
