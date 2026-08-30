package com.masakasakasama.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.masakasakasama.home.data.AppCatalog
import com.masakasakasama.home.data.Config
import com.masakasakasama.home.data.Tile
import com.masakasakasama.home.data.TileKind
import com.masakasakasama.home.focus.FocusItem
import com.masakasakasama.home.focus.FocusSnapshot
import com.masakasakasama.home.focus.PriorityEngine
import com.masakasakasama.home.github.ApkInstaller
import com.masakasakasama.home.github.GitHubReleaseClient
import com.masakasakasama.home.github.ReleaseInfo
import com.masakasakasama.home.widget.HomeWidget
import kotlinx.coroutines.launch

private val F_BG = Color(0xFF070809)
private val F_SURFACE = Color(0xFF111316)
private val F_SURFACE_2 = Color(0xFF181B20)
private val F_MUTED = Color(0xFF7E8795)
private val F_SECONDARY = Color(0xFFADB5C1)
private val F_PRIMARY = Color(0xFFF5F7FA)
private val F_GREEN = Color(0xFF33D17A)

class FocusActivity : ComponentActivity() {

    private var focus by mutableStateOf<FocusSnapshot?>(null)
    private var tiles by mutableStateOf<List<Tile>>(emptyList())
    private var selfUpdate by mutableStateOf<ReleaseInfo?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        reload()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                FocusHome()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
        checkSelfUpdate()
    }

    private fun reload() {
        focus = PriorityEngine.snapshot(this)
        tiles = Config.tiles(this)
    }

    private fun checkSelfUpdate() {
        lifecycleScope.launch {
            val latest = GitHubReleaseClient.latestRelease(
                AppCatalog.SELF_OWNER,
                AppCatalog.SELF_REPO,
            ) ?: return@launch
            selfUpdate = latest.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
        }
    }

    private fun complete(item: FocusItem) {
        if (item.actionLabel.contains("済み")) return
        PriorityEngine.complete(this, item.id)
        focus = PriorityEngine.snapshot(this)
        runCatching { HomeWidget.pushUpdate(this) }
    }

    private fun openTile(tile: Tile) {
        Config.recordTileOpen(this, tile.id)
        if (tile.kind == TileKind.APP || tile.kind == TileKind.STOCK) {
            val launch = tile.pkg?.let { packageManager.getLaunchIntentForPackage(it) }
            if (launch != null) {
                startActivity(launch)
                return
            }
        }
        tile.url?.let(::openUrl) ?: toast("${tile.title} を開けません")
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { toast("開けませんでした") }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    @Composable
    private fun FocusHome() {
        val snapshot = focus ?: return
        val update = selfUpdate
        var downloading by remember { mutableStateOf(false) }
        val launchers = Config.sortByUsage(this, tiles.filter { it.kind != TileKind.STOCK })
        val (stocks, _) = Config.cachedStock(this)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(F_BG)
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState()),
        ) {
            Header()

            if (update != null) {
                UpdateCard(update, downloading) {
                    if (!ApkInstaller.canInstall(this@FocusActivity)) {
                        ApkInstaller.requestInstallPermission(this@FocusActivity)
                    } else if (!downloading) {
                        downloading = true
                        ApkInstaller.downloadAndInstall(
                            context = this@FocusActivity,
                            apkUrl = update.apkUrl,
                            tag = "self-${update.tag}",
                            onInstallerOpened = { downloading = false },
                        ) { error ->
                            downloading = false
                            toast(error)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            SectionLabel("NOW", "いまやる1つ")
            FocusCard(snapshot.now, primary = true) { complete(snapshot.now) }

            SectionLabel("NEXT", "終わったら自動で切替")
            FocusCard(snapshot.next, primary = false) { complete(snapshot.next) }

            ProgressCard(snapshot.summary)

            if (launchers.isNotEmpty()) {
                SectionLabel("APPS", "${launchers.size}件 · 利用回数順")
                launchers.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { tile ->
                            AppCard(tile, Modifier.weight(1f)) { openTile(tile) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (stocks.isNotEmpty()) {
                SectionLabel("MARKETS", "参考情報")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(F_SURFACE)
                        .padding(16.dp),
                ) {
                    Text(
                        stocks.take(3).joinToString("   ") { (symbol, price, change) ->
                            val sign = if (change >= 0) "+" else ""
                            "$symbol ${formatPrice(price)}  $sign${"%.1f".format(change)}%"
                        },
                        color = F_SECONDARY,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(36.dp))
        }
    }

    @Composable
    private fun Header() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Home",
                    color = F_PRIMARY,
                    fontSize = 38.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-1.1).sp,
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(F_GREEN))
                    Spacer(Modifier.width(7.dp))
                    Text("Action first · v${BuildConfig.VERSION_NAME}", color = F_MUTED, fontSize = 12.sp)
                }
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(F_SURFACE)
                    .clickable { startActivity(Intent(this@FocusActivity, MainActivity::class.java)) },
                contentAlignment = Alignment.Center,
            ) {
                Text("⚙", color = F_PRIMARY, fontSize = 17.sp)
            }
        }
    }

    @Composable
    private fun SectionLabel(title: String, detail: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 9.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(title, color = F_PRIMARY, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            Text(detail, color = F_MUTED, fontSize = 11.sp)
        }
    }

    @Composable
    private fun FocusCard(item: FocusItem, primary: Boolean, onComplete: () -> Unit) {
        val accent = if (primary) F_GREEN else F_SECONDARY
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (primary) Color(0xFF111A16) else F_SURFACE)
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (primary) F_GREEN.copy(alpha = 0.15f) else F_SURFACE_2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(item.emoji, fontSize = 22.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, color = F_PRIMARY, fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(item.progress, color = accent, fontSize = 12.sp,
                        fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(item.detail, color = F_SECONDARY, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (item.actionLabel.contains("済み")) F_SURFACE_2 else if (primary) F_GREEN else F_SURFACE_2)
                    .clickable(enabled = !item.actionLabel.contains("済み")) { onComplete() }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.actionLabel,
                    color = if (primary && !item.actionLabel.contains("済み")) Color(0xFF07110B) else F_PRIMARY,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    @Composable
    private fun ProgressCard(summary: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(F_SURFACE)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("WEEK", color = F_MUTED, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp)
            Spacer(Modifier.width(12.dp))
            Text(summary, color = F_SECONDARY, fontSize = 12.sp)
        }
    }

    @Composable
    private fun AppCard(tile: Tile, modifier: Modifier, onClick: () -> Unit) {
        val accent = Color(tile.colorArgb)
        Column(
            modifier = modifier
                .height(126.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(F_SURFACE)
                .clickable { onClick() }
                .padding(15.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Text(tile.emoji, fontSize = 19.sp) }
                Spacer(Modifier.weight(1f))
                Text(if (tile.kind == TileKind.APP) "APP" else "WEB", color = accent,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text(tile.category, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.0.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(tile.title, color = F_PRIMARY, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    @Composable
    private fun UpdateCard(update: ReleaseInfo, busy: Boolean, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF10251B))
                .clickable(enabled = !busy) { onClick() }
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("↓", color = F_GREEN, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Home ${update.tag}", color = F_PRIMARY, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold)
                Text(if (busy) "ダウンロード中…" else "新しいバージョンをインストール",
                    color = F_SECONDARY, fontSize = 12.sp)
            }
            Text(if (busy) "···" else "更新", color = F_GREEN, fontSize = 12.sp,
                fontWeight = FontWeight.Bold)
        }
    }

    private fun formatPrice(price: Double): String = when {
        price >= 1000 -> "%,.0f".format(price)
        price >= 100 -> "%.1f".format(price)
        else -> "%.2f".format(price)
    }
}
