package com.masakasakasama.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.lifecycleScope
import com.masakasakasama.home.BuildConfig
import com.masakasakasama.home.data.AppCatalog
import com.masakasakasama.home.data.AppEntry
import com.masakasakasama.home.data.Target
import com.masakasakasama.home.github.ApkInstaller
import com.masakasakasama.home.github.GitHubReleaseClient
import com.masakasakasama.home.github.ReleaseInfo
import com.masakasakasama.home.stock.StockLive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var selfUpdate by mutableStateOf<ReleaseInfo?>(null)
    private var stockSummary by mutableStateOf<String?>(null)

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
    }

    override fun onResume() {
        super.onResume()
        // Re-check on every foreground, not just the first cold start.
        checkSelfUpdate()
        refreshStocks()
    }

    private fun refreshStocks() {
        lifecycleScope.launch {
            val q = StockLive.quotes(applicationContext)
            if (q.isNotEmpty()) stockSummary = StockLive.summarize(q)
        }
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Home",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (update != null) Color(0xFF0A84FF)
                            else Color(0xFF30D158)
                        )
                )
                Spacer(Modifier.size(7.dp))
                Text(
                    text = if (update != null)
                        "更新あり (${update.tag}) ・ v${BuildConfig.VERSION_NAME}"
                    else
                        "最新版 ・ v${BuildConfig.VERSION_NAME}",
                    color = Color(0xFF8E8E93),
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(24.dp))

            if (update != null) {
                if (ApkInstaller.canInstall(context)) {
                    Banner(updateStatus ?: "新しいバージョン (${update.tag}) を準備中…")
                } else {
                    UpdateBanner(
                        tag = update.tag,
                        onUpdate = {
                            ApkInstaller.requestInstallPermission(context)
                        }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            AppCatalog.apps.forEach { app ->
                GlassRow(
                    app = app,
                    subtitle = when (val t = app.target) {
                        is Target.InstalledApp ->
                            stockSummary ?: "株価ウィジェットを開く"
                        is Target.Web -> Uri.parse(t.url).host ?: "ウェブを開く"
                    },
                ) { openApp(app) }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    @Composable
    private fun Banner(text: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x14FFFFFF))
                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(20.dp))
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0A84FF))
            )
            Spacer(Modifier.size(10.dp))
            Text(text = text, color = Color(0xFFE5E5EA), fontSize = 14.sp)
        }
    }

    @Composable
    private fun UpdateBanner(tag: String, onUpdate: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x14FFFFFF))
                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(20.dp))
                .padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "新しいバージョン ($tag)：インストール許可が必要です",
                color = Color(0xFFE5E5EA),
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.size(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF0A84FF))
                    .clickable { onUpdate() }
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text(
                    text = "許可",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    @Composable
    private fun GlassRow(
        app: AppEntry,
        subtitle: String,
        onClick: () -> Unit,
    ) {
        val shape = RoundedCornerShape(22.dp)
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.98f else 1f,
            animationSpec = spring(),
            label = "rowScale",
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .shadow(8.dp, shape, clip = false)
                .clip(shape)
                .background(if (pressed) Color(0x1FFFFFFF) else Color(0x12FFFFFF))
                .border(1.dp, Color(0x1FFFFFFF), shape)
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                ) { onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(app.color.copy(alpha = 0.20f))
                    .border(1.dp, app.color.copy(alpha = 0.30f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = app.emoji, fontSize = 24.sp)
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFF8E8E93),
                    fontSize = 13.sp,
                )
            }
            Text(
                text = "›",
                color = Color(0xFF8E8E93),
                fontSize = 22.sp,
            )
        }
    }
}
