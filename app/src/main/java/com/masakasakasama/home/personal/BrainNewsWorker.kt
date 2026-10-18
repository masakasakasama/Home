package com.masakasakasama.home.personal

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Run on Android's permitted schedule, not an always-running background process. */
class BrainNewsWorker(private val context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val token = PersonalToken.load(context)
        if (token.isBlank()) return Result.success()
        return try {
            val batches = BrainInbox.fetch(token)
            val newest = batches.firstOrNull()?.at ?: return Result.success()
            val state = context.getSharedPreferences("personal_brain_news", Context.MODE_PRIVATE)
            val previous = state.getString("latest", null)
            if (previous == null) {
                state.edit().putString("latest", newest).apply()
                return Result.success() // No backlog notification on first run
            }
            if (previous != newest) {
                val newBatches = batches.takeWhile { it.at != previous }
                val alert = newBatches.flatMap { it.headlines }.maxByOrNull { it.score }
                if (alert != null) postLocalNotification(alert)
                state.edit().putString("latest", newest).apply()
            }
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }

    private fun postLocalNotification(headline: BrainHeadline) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("personal_brain_news", "Brainの重要ニュース",
                NotificationManager.IMPORTANCE_DEFAULT)
        )
        val intent = Intent(context, PersonalInboxActivity::class.java)
        val pending = PendingIntent.getActivity(context, 9874, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val note = Notification.Builder(context, "personal_brain_news")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Brain: 重要ニュース ${headline.score}/100")
            .setContentText(headline.summary)
            .setStyle(Notification.BigTextStyle().bigText(headline.summary))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(9874, note)
    }

    companion object {
        fun schedule(context: Context) {
            val work = PeriodicWorkRequestBuilder<BrainNewsWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "PersonalHomeBrainNews", ExistingPeriodicWorkPolicy.KEEP, work)
        }
    }
}
