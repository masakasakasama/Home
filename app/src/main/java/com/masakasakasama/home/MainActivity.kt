package com.masakasakasama.home

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.masakasakasama.home.data.AppCatalog
import com.masakasakasama.home.data.AppEntry
import com.masakasakasama.home.github.ApkInstaller
import com.masakasakasama.home.github.GitHubReleaseClient
import com.masakasakasama.home.github.ReleaseInfo
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    HomeScreen()
                }
            }
        }
        checkSelfUpdate()
    }

    private var selfUpdate by mutableStateOf<ReleaseInfo?>(null)

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
        val scope = rememberCoroutineScope()
        var status by remember { mutableStateOf<String?>(null) }
        val update = selfUpdate

        fun toast(msg: String) =
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

        fun installFrom(owner: String, repo: String) {
            if (!ApkInstaller.canInstall(context)) {
                toast("「不明なアプリのインストール」を許可してください")
                ApkInstaller.requestInstallPermission(context)
                return
            }
            status = "$repo の最新版を確認中…"
            scope.launch {
                val release = GitHubReleaseClient.latestRelease(owner, repo)
                if (release == null) {
                    status = null
                    toast("$repo のリリースが見つかりません")
                    return@launch
                }
                status = "$repo をダウンロード中…"
                ApkInstaller.downloadAndInstall(
                    context = context,
                    apkUrl = release.apkUrl,
                    tag = "$repo-${release.tag}",
                ) { err ->
                    status = null
                    toast(err)
                }
            }
        }

        fun openApp(app: AppEntry) {
            val pkg = app.packageName
            if (pkg != null) {
                val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launch != null) {
                    context.startActivity(launch)
                    return
                }
            }
            installFrom(app.owner, app.repo)
        }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "Home",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )

            if (update != null) {
                UpdateBanner(
                    tag = update.tag,
                    onUpdate = {
                        if (!ApkInstaller.canInstall(context)) {
                            toast("「不明なアプリのインストール」を許可してください")
                            ApkInstaller.requestInstallPermission(context)
                            return@UpdateBanner
                        }
                        ApkInstaller.downloadAndInstall(
                            context = context,
                            apkUrl = update.apkUrl,
                            tag = "self-${update.tag}",
                        ) { err -> toast(err) }
                    }
                )
            }

            status?.let {
                Text(
                    text = it,
                    color = Color(0xFF90CAF9),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

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
                text = "新しいバージョン ($tag) があります",
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onUpdate) { Text("更新") }
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
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
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
