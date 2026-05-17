package com.masakasakasama.home.github

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

object ApkInstaller {

    /** True when the OS will let us launch an APK install intent. */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Sends the user to the "Install unknown apps" settings screen. */
    fun requestInstallPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Downloads [apkUrl] into the app's external Download dir and launches the
     * system installer when finished. [tag] is used to name the file so each
     * version is cached separately.
     */
    fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        tag: String,
        onError: (String) -> Unit,
    ) {
        val fileName = "Home-update-$tag.apk"
        val target = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        if (target.exists()) target.delete()

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("ダウンロード中: $fileName")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
            .setMimeType("application/vnd.android.package-archive")

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = try {
            dm.enqueue(request)
        } catch (e: Exception) {
            onError("ダウンロードを開始できませんでした: ${e.message}")
            return
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(
                    DownloadManager.EXTRA_DOWNLOAD_ID, -1
                )
                if (id != downloadId) return
                context.unregisterReceiver(this)

                if (!target.exists()) {
                    onError("ダウンロードに失敗しました")
                    return
                }
                launchInstaller(context, target, onError)
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    private fun launchInstaller(
        context: Context,
        apk: File,
        onError: (String) -> Unit,
    ) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            onError("インストーラーを起動できませんでした: ${e.message}")
        }
    }
}
