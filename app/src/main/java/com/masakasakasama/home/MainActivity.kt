package com.masakasakasama.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.masakasakasama.home.BuildConfig
import com.masakasakasama.home.data.AppCatalog
import com.masakasakasama.home.data.AppEntry
import com.masakasakasama.home.data.Target
import com.masakasakasama.home.github.ApkInstaller
import com.masakasakasama.home.github.GitHubReleaseClient
import com.masakasakasama.home.github.ReleaseInfo
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var selfUpdate by mutableStateOf<ReleaseInfo?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    HomeScreen()
                }
            }
        }
        checkSelfUpdate()
    }

    private fun checkSelfUpdate() {
        lifecycleScope.launch {
            val latest = GitHubReleaseClient.latestRelease(
                AppCatalog.SELF_OWNER, AppCatalog.SELF_REPO
            ) ?: return@launch
            if (latest.versionCode > BuildConfig.VERSION_CODE) {
                selfUpdate = latest
            }
        }
    }

    @Composable
    private fun HomeScreen() {
        val context = LocalContext.current
        val update = selfUpdate
        var updateStatus by remember { mutableStateOf<String?>(null) }
        var downloadStarted by remember { mutableStateOf(false) }

        fun toast(msg: String) =
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

        // Auto-download the update on launch. The OS install confirmation
        // still appears at the end (unavoidable for side-loaded apps).
        LaunchedEffect(update?.tag) {
            if (update == null || downloadStarted) return@LaunchedEffect
            if (!ApkInstaller.canInstall(context)) return@LaunchedEffect
            downloadStarted = true
            updateStatus = "新しいバージョン (${update.tag}) をダウンロード中…"
            ApkInstaller.downloadAndInstall(
                context = context,
                apkUrl = update.apkUrl,
                tag = "self-${update.tag}",
            ) { err ->
                updateStatus = null
                downloadStarted = false
                toast(err)
            }
        }

        fun openApp(app: AppEntry) {
            when (val t = app.target) {
                is Target.Web -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(t.url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                        .onFailure { toast("ブラウザを開けませんでした") }
                }

                is Target.InstalledApp -> {
                    val launch = context.packageManager
                        .getLaunchIntentForPackage(t.packageName)
                    if (launch != null) {
                        context.startActivity(launch)
                    } else {
                        toast("${app.title} がインストールされていません")
                    }
                }

            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "Home",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 24.dp, bottom = 2.dp)
            )
            Text(
                text = if (update != null)
                    "バージョン ${BuildConfig.VERSION_NAME} ・ 更新あり (${update.tag}) → 自動更新します"
                else
                    "バージョン ${BuildConfig.VERSION_NAME} ・ 最新版です",
                color = if (update != null) Color(0xFF90CAF9) else Color(0xFF90A4AE),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (update != null) {
                if (ApkInstaller.canInstall(context)) {
                    // Download is automatic; show progress text only.
                    Banner(updateStatus ?: "新しいバージョン (${update.tag}) を準備中…")
                } else {
                    // Need the "install unknown apps" permission first.
                    UpdateBanner(
                        tag = update.tag,
                        onUpdate = {
                            ApkInstaller.requestInstallPermission(context)
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(AppCatalog.apps) { app ->
                    AppTile(app) { openApp(app) }
                }
            }
        }
    }

    @Composable
    private fun Banner(text: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1565C0))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = text, color = Color.White, fontSize = 15.sp)
        }
    }

    @Composable
    private fun UpdateBanner(tag: String, onUpdate: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1565C0))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "新しいバージョン ($tag): インストール許可が必要です",
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onUpdate) { Text("許可") }
        }
    }

    @Composable
    private fun AppTile(app: AppEntry, onClick: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(app.color)
                .clickable { onClick() }
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(text = app.emoji, fontSize = 48.sp)
            }
            Text(
                text = app.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
