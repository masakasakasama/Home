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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Home",
                style = TextStyle(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF7AA8FF), Color(0xFFC58AF9), Color(0xFF6FE0C8))
                    )
                ),
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x14FFFFFF))
                    .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (update != null) Color(0xFF5AA9FF)
                            else Color(0xFF3DDC84)
                        )
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = if (update != null)
                        "v${BuildConfig.VERSION_NAME}  ・  更新あり (${update.tag})"
                    else
                        "v${BuildConfig.VERSION_NAME}  ・  最新版",
                    color = if (update != null) Color(0xFFAFCBFF) else Color(0xFFA8B3C2),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(22.dp))

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

            val apps = AppCatalog.apps
            val hero = apps.first()
            HeroTile(
                app = hero,
                subtitle = "タップして株価ウィジェットを開く",
            ) { openApp(hero) }
            Spacer(Modifier.height(14.dp))

            apps.drop(1).chunked(2).forEach { rowApps ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    rowApps.forEach { app ->
                        GlassTile(app, Modifier.weight(1f)) { openApp(app) }
                    }
                    if (rowApps.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
            }
            Spacer(Modifier.height(8.dp))
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
    private fun HeroTile(
        app: AppEntry,
        subtitle: String,
        onClick: () -> Unit,
    ) {
        val shape = RoundedCornerShape(28.dp)
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.975f else 1f,
            animationSpec = spring(),
            label = "heroScale",
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .scale(scale)
                .shadow(18.dp, shape, clip = false)
                .clip(shape)
                .background(Color(0xFF0F1310))
                .background(
                    Brush.linearGradient(
                        listOf(app.color.copy(alpha = 0.40f), Color.Transparent)
                    )
                )
                .border(1.dp, app.color.copy(alpha = 0.50f), shape)
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                ) { onClick() }
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(app.color.copy(alpha = 0.26f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = app.emoji, fontSize = 34.sp)
            }
            Spacer(Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.title,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFFB6C2B8),
                    fontSize = 13.sp,
                )
            }
            Text(
                text = "›",
                color = app.color.copy(alpha = 0.75f),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    @Composable
    private fun GlassTile(
        app: AppEntry,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
    ) {
        val shape = RoundedCornerShape(24.dp)
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.955f else 1f,
            animationSpec = spring(),
            label = "tileScale",
        )
        Box(
            modifier = modifier
                .aspectRatio(1f)
                .scale(scale)
                .shadow(12.dp, shape, clip = false)
                .clip(shape)
                .background(Color(0xFF12151D))
                .background(
                    Brush.linearGradient(
                        listOf(app.color.copy(alpha = 0.30f), Color.Transparent)
                    )
                )
                .border(1.dp, app.color.copy(alpha = 0.38f), shape)
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                ) { onClick() }
                .padding(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(50.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(app.color.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = app.emoji, fontSize = 26.sp)
            }
            Text(
                text = "›",
                color = app.color.copy(alpha = 0.55f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd),
            )
            Text(
                text = app.title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}
