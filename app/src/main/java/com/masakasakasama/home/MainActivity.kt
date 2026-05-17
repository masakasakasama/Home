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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
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
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF0B1020),
                                    Color(0xFF0A0A0A),
                                )
                            )
                        )
                ) {
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "Home",
                style = TextStyle(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF8AB4F8), Color(0xFFC58AF9))
                    )
                ),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (update != null) Color(0xFF64B5F6)
                            else Color(0xFF4CAF50)
                        )
                )
                Spacer(Modifier.size(7.dp))
                Text(
                    text = if (update != null)
                        "v${BuildConfig.VERSION_NAME} ・ 更新あり (${update.tag}) → 自動更新します"
                    else
                        "v${BuildConfig.VERSION_NAME} ・ 最新版です",
                    color = if (update != null) Color(0xFF90CAF9) else Color(0xFF9AA7B2),
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(18.dp))

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
                contentPadding = PaddingValues(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
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
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF1A2A6C), Color(0xFF2E5CE6))
                    )
                )
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "⬇  $text", color = Color.White, fontSize = 15.sp)
        }
    }

    @Composable
    private fun UpdateBanner(tag: String, onUpdate: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF1A2A6C), Color(0xFF2E5CE6))
                    )
                )
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                .padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "新しいバージョン ($tag): インストール許可が必要です",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onUpdate) { Text("許可") }
        }
    }

    @Composable
    private fun AppTile(app: AppEntry, onClick: () -> Unit) {
        val shape = RoundedCornerShape(26.dp)
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.95f else 1f,
            animationSpec = spring(),
            label = "tileScale",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .scale(scale)
                .shadow(10.dp, shape, clip = false)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(app.color, lerp(app.color, Color.Black, 0.45f))
                    )
                )
                .border(1.dp, Color(0x1FFFFFFF), shape)
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                ) { onClick() }
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0x26FFFFFF)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = app.emoji, fontSize = 30.sp)
            }
            Text(
                text = app.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
