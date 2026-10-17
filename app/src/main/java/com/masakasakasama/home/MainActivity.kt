package com.masakasakasama.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.masakasakasama.home.data.AppCatalog
import com.masakasakasama.home.data.Config
import com.masakasakasama.home.data.Tile
import com.masakasakasama.home.data.TileKind
import com.masakasakasama.home.data.WebStatus
import com.masakasakasama.home.github.ApkInstaller
import com.masakasakasama.home.github.GitHubReleaseClient
import com.masakasakasama.home.github.ReleaseInfo
import com.masakasakasama.home.stock.Quote
import com.masakasakasama.home.stock.StockLive
import com.masakasakasama.home.tiles.FitnessTip
import com.masakasakasama.home.tiles.NewsFeed
import com.masakasakasama.home.tiles.NewsLive
import com.masakasakasama.home.tiles.WebStatusClient
import com.masakasakasama.home.widget.HomeWidget
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.util.Date
import java.util.Locale

private val BG = Color(0xFF070809)
private val SURFACE = Color(0xFF111316)
private val SURFACE_2 = Color(0xFF181B20)
private val STROKE = Color(0xFF242831)
private val MUTED = Color(0xFF7E8795)
private val SECONDARY = Color(0xFFADB5C1)
private val PRIMARY = Color(0xFFF5F7FA)
private val GREEN = Color(0xFF33D17A)
private val RED = Color(0xFFFF5E66)

class MainActivity : ComponentActivity() {

    private var selfUpdate by mutableStateOf<ReleaseInfo?>(null)
    private var tiles by mutableStateOf<List<Tile>>(emptyList())
    private var quotes by mutableStateOf<List<Quote>>(emptyList())
    private var news by mutableStateOf(NewsFeed(emptyList(), null))
    private var statuses by mutableStateOf<Map<String, WebStatus>>(emptyMap())
    private var refreshing by mutableStateOf(false)
    private var lastSync by mutableStateOf(0L)
    private var liveOk by mutableStateOf(true)
    private var showSettings by mutableStateOf(false)
    private var lastUpdateCheckAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        seedFromCache()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.fillMaxSize().background(BG)) {
                    BackHandler(enabled = showSettings) { showSettings = false }
                    if (showSettings) SettingsScreen() else Dashboard()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reloadConfig()
        checkSelfUpdate()
        refresh()
    }

    private fun reloadConfig() {
        tiles = Config.tiles(this)
    }

    private fun seedFromCache() {
        reloadConfig()
        val (cachedQuotes, _) = Config.cachedStock(this)
        quotes = cachedQuotes.map { Quote(it.first, it.second, it.third) }
        val (items, age, _) = Config.cachedNews(this)
        news = NewsFeed(items, age)
        statuses = tiles.mapNotNull { tile ->
            Config.cachedStatus(this, tile.id)?.let { tile.id to it }
        }.toMap()
        lastSync = Config.lastSync(this)
    }

    private fun refresh() {
        if (refreshing) return
        refreshing = true
        lifecycleScope.launch {
            try {
                var ok = true
                runCatching {
                    val latestQuotes = StockLive.quotes(
                        applicationContext,
                        Config.watchlist(applicationContext),
                    )
                    if (latestQuotes.isNotEmpty()) {
                        quotes = latestQuotes
                        Config.cacheStock(
                            applicationContext,
                            latestQuotes.map { Triple(it.symbol, it.price, it.changePct) },
                        )
                    } else if (quotes.isEmpty()) {
                        ok = false
                    }
                }.onFailure { ok = false }

                runCatching {
                    val feed = NewsLive.feed(Config.newsFeed(applicationContext))
                    if (feed.items.isNotEmpty()) {
                        news = feed
                        Config.cacheNews(applicationContext, feed.items, feed.ageMinutes)
                    } else if (news.items.isEmpty()) {
                        ok = false
                    }
                }.onFailure { ok = false }

                val freshStatuses = statuses.toMutableMap()
                tiles.filter { it.kind == TileKind.WEB }.forEach { tile ->
                    val statusUrl = tile.statusUrl ?: return@forEach
                    runCatching {
                        WebStatusClient.fetch(statusUrl)?.let { status ->
                            freshStatuses[tile.id] = status
                            Config.cacheStatus(applicationContext, tile.id, status)
                        }
                    }
                }
                statuses = freshStatuses

                liveOk = ok
                lastSync = Config.lastSync(applicationContext)
                runCatching { HomeWidget.pushUpdate(applicationContext) }
            } finally {
                refreshing = false
            }
        }
    }

    private fun checkSelfUpdate(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUpdateCheckAt < 10 * 60_000L) return
        lastUpdateCheckAt = now
        lifecycleScope.launch {
            val latest = GitHubReleaseClient.latestRelease(
                AppCatalog.SELF_OWNER,
                AppCatalog.SELF_REPO,
            ) ?: return@launch
            selfUpdate = latest.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
        }
    }

    private fun openTile(tile: Tile) {
        Config.recordTileOpen(this, tile.id)
        when (tile.kind) {
            TileKind.STOCK, TileKind.APP -> {
                val pkg = tile.pkg
                val launchIntent = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else if (tile.url != null) {
                    openUrl(tile.url)
                } else {
                    toast("${tile.title} がインストールされていません")
                }
            }
            else -> tile.url?.let(::openUrl)
        }
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { toast("開けませんでした") }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    @Composable
    private fun Dashboard() {
        val context = LocalContext.current
        val update = selfUpdate
        var downloadStarted by remember { mutableStateOf(false) }
        var updateStatus by remember { mutableStateOf<String?>(null) }
        val featured = tiles.filter {
            it.kind == TileKind.STOCK || it.kind == TileKind.NEWS || it.kind == TileKind.FITNESS
        }
        val launchers = Config.sortByUsage(this, tiles.filterNot { it in featured })

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState()),
        ) {
            DashboardHeader(
                status = when {
                    updateStatus != null -> updateStatus!!
                    update != null -> "${update.tag} available"
                    refreshing -> "Syncing"
                    !liveOk && lastSync > 0L -> "Offline · ${clock(lastSync)}"
                    lastSync > 0L -> "Updated ${clock(lastSync)}"
                    else -> "${tiles.size} apps · v${BuildConfig.VERSION_NAME}"
                },
                warning = update != null || (!liveOk && lastSync > 0L),
                onRefresh = {
                    checkSelfUpdate(force = true)
                    refresh()
                },
                onSettings = { showSettings = true },
            )

            if (update != null) {
                UpdateBanner(
                    update = update,
                    busy = downloadStarted,
                    status = updateStatus,
                ) {
                    if (!ApkInstaller.canInstall(context)) {
                        ApkInstaller.requestInstallPermission(context)
                    } else if (!downloadStarted) {
                        downloadStarted = true
                        updateStatus = "${update.tag} をダウンロード中…"
                        ApkInstaller.downloadAndInstall(
                            context = context,
                            apkUrl = update.apkUrl,
                            tag = "self-${update.tag}",
                            onInstallerOpened = {
                                updateStatus = null
                                downloadStarted = false
                            },
                        ) { error ->
                            updateStatus = null
                            downloadStarted = false
                            toast(error)
                        }
                    }
                }
            }

            if (tiles.isEmpty()) {
                EmptyState()
            }

            if (featured.isNotEmpty()) {
                SectionTitle("Today", "必要な情報だけ")
                featured.forEach { tile ->
                    when (tile.kind) {
                        TileKind.STOCK -> StockCard(tile)
                        TileKind.NEWS -> NewsCard(tile)
                        TileKind.FITNESS -> FitnessCard(tile)
                        else -> Unit
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (launchers.isNotEmpty()) {
                SectionTitle("Apps", "${launchers.size} items")
                LauncherGrid(launchers)
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    @Composable
    private fun DashboardHeader(
        status: String,
        warning: Boolean,
        onRefresh: () -> Unit,
        onSettings: () -> Unit,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Home",
                        color = PRIMARY,
                        fontSize = 38.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-1.2).sp,
                    )
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (warning) RED else GREEN),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = status,
                            color = if (warning) SECONDARY else MUTED,
                            fontSize = 12.sp,
                            letterSpacing = 0.2.sp,
                        )
                    }
                }
                HeaderAction(if (refreshing) "···" else "↻", onRefresh)
                Spacer(Modifier.width(8.dp))
                HeaderAction("⚙", onSettings)
            }
        }
    }

    @Composable
    private fun HeaderAction(label: String, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SURFACE)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = PRIMARY, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
    }

    @Composable
    private fun UpdateBanner(
        update: ReleaseInfo,
        busy: Boolean,
        status: String?,
        onClick: () -> Unit,
    ) {
        val context = LocalContext.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF10251B))
                .clickable(enabled = !busy) { onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(GREEN.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("↓", color = GREEN, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Home ${update.tag}",
                    color = PRIMARY,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    status ?: if (ApkInstaller.canInstall(context)) {
                        "アップデートをインストール"
                    } else {
                        "インストール許可が必要"
                    },
                    color = SECONDARY,
                    fontSize = 12.sp,
                )
            }
            Text(if (busy) "···" else "更新", color = GREEN, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold)
        }
    }

    @Composable
    private fun EmptyState() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(SURFACE)
                .padding(20.dp),
        ) {
            Text("No apps yet", color = PRIMARY, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text("設定から表示するタイルを追加できます", color = MUTED, fontSize = 13.sp)
        }
    }

    @Composable
    private fun SectionTitle(title: String, detail: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = title,
                color = PRIMARY,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
                modifier = Modifier.weight(1f),
            )
            Text(detail, color = MUTED, fontSize = 11.sp)
        }
    }

    @Composable
    private fun SurfaceCard(
        onClick: () -> Unit,
        content: @Composable () -> Unit,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(SURFACE)
                .clickable { onClick() }
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            content()
        }
    }

    @Composable
    private fun CardHeader(
        tile: Tile,
        eyebrow: String,
        trailing: @Composable () -> Unit = {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(tile.colorArgb).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(tile.emoji, color = PRIMARY, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    eyebrow.uppercase(),
                    color = Color(tile.colorArgb),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    tile.title,
                    color = PRIMARY,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing()
        }
    }

    private fun changeColor(percent: Double) = when {
        percent > 0 -> GREEN
        percent < 0 -> RED
        else -> SECONDARY
    }

    private fun changeText(percent: Double): String {
        val sign = if (percent >= 0) "+" else "−"
        return "$sign%.2f%%".format(kotlin.math.abs(percent))
    }

    @Composable
    private fun StockCard(tile: Tile) {
        SurfaceCard({ openTile(tile) }) {
            CardHeader(tile, "Markets") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (quotes.isEmpty()) MUTED else GREEN),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (quotes.isEmpty()) "OFFLINE" else if (!liveOk) "CACHED" else "LIVE",
                        color = if (quotes.isEmpty() || !liveOk) MUTED else GREEN,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            if (quotes.isEmpty()) {
                Text("株価を取得できません", color = SECONDARY, fontSize = 13.sp)
                return@SurfaceCard
            }

            val head = quotes.first()
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(StockLive.label(head.symbol), color = MUTED, fontSize = 12.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        StockLive.formatPrice(head.price),
                        color = PRIMARY,
                        fontSize = 42.sp,
                        lineHeight = 46.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-1.2).sp,
                    )
                }
                ChangePill(head.changePct)
            }

            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(STROKE))

            quotes.drop(1).forEach { quote ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        StockLive.label(quote.symbol),
                        color = SECONDARY,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        StockLive.formatPrice(quote.price),
                        color = PRIMARY,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        changeText(quote.changePct),
                        color = changeColor(quote.changePct),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }

    @Composable
    private fun ChangePill(percent: Double) {
        val color = changeColor(percent)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                changeText(percent),
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

    @Composable
    private fun NewsCard(tile: Tile) {
        SurfaceCard({ openTile(tile) }) {
            CardHeader(tile, "News") {
                Text(NewsLive.ago(news.ageMinutes), color = MUTED, fontSize = 11.sp)
            }
            Spacer(Modifier.height(18.dp))

            if (news.items.isEmpty()) {
                Text("目立ったニュースはありません", color = SECONDARY, fontSize = 13.sp)
                return@SurfaceCard
            }

            news.items.take(3).forEachIndexed { index, headline ->
                if (index > 0) {
                    Spacer(Modifier.height(13.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(STROKE))
                    Spacer(Modifier.height(13.dp))
                }
                Row {
                    Text(
                        "%02d".format(index + 1),
                        color = MUTED,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 3.dp, end = 12.dp),
                    )
                    Text(
                        headline,
                        color = PRIMARY,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    @Composable
    private fun FitnessCard(tile: Tile) {
        val days = Config.trainDays(this)
        SurfaceCard({ openTile(tile) }) {
            CardHeader(tile, "Training")
            Spacer(Modifier.height(20.dp))

            val today = FitnessTip.today()
            Row(modifier = Modifier.fillMaxWidth()) {
                FitnessTip.week.forEach { day ->
                    val train = day.value in days
                    val isToday = day == today
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            FitnessTip.jp(day),
                            color = if (isToday) PRIMARY else if (train) SECONDARY else MUTED,
                            fontSize = 13.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        )
                        Spacer(Modifier.height(9.dp))
                        Box(
                            Modifier
                                .size(if (train) 8.dp else 5.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isToday && train -> Color(tile.colorArgb)
                                        train -> PRIMARY
                                        else -> STROKE
                                    },
                                ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(STROKE))
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Metric("今週", "${days.size}回", Modifier.weight(1f))
                Metric("次回", FitnessTip.nextShort(days), Modifier)
            }
        }
    }

    @Composable
    private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
        Column(modifier = modifier) {
            Text(label, color = MUTED, fontSize = 10.sp, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(5.dp))
            Text(value, color = PRIMARY, fontSize = 23.sp, fontWeight = FontWeight.Light)
        }
    }

    @Composable
    private fun LauncherGrid(launchers: List<Tile>) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            launchers.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    pair.forEach { tile ->
                        LauncherCard(tile, statuses[tile.id], Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    @Composable
    private fun LauncherCard(
        tile: Tile,
        status: WebStatus?,
        modifier: Modifier = Modifier,
    ) {
        val installed = tile.pkg?.let { packageManager.getLaunchIntentForPackage(it) != null } == true
        val state = status?.primary?.takeIf { it.isNotBlank() } ?: when {
            tile.id == "cpre" -> "認証"
            tile.kind == TileKind.APP && installed -> "READY"
            tile.kind == TileKind.APP -> "GET"
            else -> "WEB"
        }

        Column(
            modifier = modifier
                .height(124.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SURFACE)
                .clickable { openTile(tile) }
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color(tile.colorArgb).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tile.emoji, color = PRIMARY, fontSize = 19.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    state,
                    color = Color(tile.colorArgb),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                tile.title,
                color = PRIMARY,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                status?.detail?.takeIf { it.isNotBlank() } ?: tile.category,
                color = MUTED,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    private fun clock(ms: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

    @Composable
    private fun SettingsScreen() {
        val context = this
        var feed by remember { mutableStateOf(Config.newsFeed(context)) }
        var watch by remember { mutableStateOf(Config.watchlist(context).joinToString(", ")) }
        var refreshMin by remember { mutableStateOf(Config.refreshMin(context)) }
        var dayState by remember { mutableStateOf(Config.trainDays(context)) }
        var order by remember { mutableStateOf(Config.allTilesOrdered(context)) }
        var hidden by remember {
            mutableStateOf(order.filter { Config.isHidden(context, it.id) }.map { it.id }.toSet())
        }
        var newTitle by remember { mutableStateOf("") }
        var newUrl by remember { mutableStateOf("") }
        var bump by remember { mutableStateOf(0) }

        fun persistAndClose() {
            Config.setNewsFeed(context, feed)
            Config.setWatchlist(
                context,
                watch.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            )
            Config.setRefreshMin(context, refreshMin)
            Config.setTrainDays(context, dayState)
            Config.setOrder(context, order.map { it.id })
            order.forEach { Config.setHidden(context, it.id, it.id in hidden) }
            showSettings = false
            reloadConfig()
            refresh()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Settings",
                        color = PRIMARY,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.7).sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Home の表示とデータ設定", color = MUTED, fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(PRIMARY)
                        .clickable { persistAndClose() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text("Done", color = BG, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            SettingLabel("NEWS FEED")
            Field(feed, { feed = it })

            SettingLabel("STOCK WATCHLIST")
            Field(watch, { watch = it })

            SettingLabel("REFRESH")
            Row {
                listOf(15, 30, 60).forEach { minutes ->
                    Chip("${minutes}m", refreshMin == minutes) { refreshMin = minutes }
                    Spacer(Modifier.width(8.dp))
                }
            }

            SettingLabel("TRAINING DAYS")
            Row {
                DayOfWeek.values().forEach { day ->
                    val enabled = day.value in dayState
                    Chip(FitnessTip.jp(day), enabled) {
                        dayState = if (enabled) dayState - day.value else dayState + day.value
                    }
                    Spacer(Modifier.width(5.dp))
                }
            }

            SettingLabel("APP ORDER")
            bump.let {
                order.forEachIndexed { index, tile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Color(tile.colorArgb).copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(tile.emoji, fontSize = 16.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            tile.title,
                            color = PRIMARY,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ReorderButton("↑", index > 0) {
                            val mutable = order.toMutableList()
                            val previous = mutable[index - 1]
                            mutable[index - 1] = mutable[index]
                            mutable[index] = previous
                            order = mutable
                            bump++
                        }
                        Spacer(Modifier.width(4.dp))
                        ReorderButton("↓", index < order.size - 1) {
                            val mutable = order.toMutableList()
                            val next = mutable[index + 1]
                            mutable[index + 1] = mutable[index]
                            mutable[index] = next
                            order = mutable
                            bump++
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = tile.id !in hidden,
                            onCheckedChange = { visible ->
                                hidden = if (visible) hidden - tile.id else hidden + tile.id
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = GREEN,
                            ),
                        )
                        if (Config.isCustom(tile.id)) {
                            Spacer(Modifier.width(6.dp))
                            ReorderButton("×", true) {
                                Config.removeCustomTile(context, tile.id)
                                order = order.filter { it.id != tile.id }
                                bump++
                            }
                        }
                    }
                }
            }

            SettingLabel("ADD LINK")
            Field(newTitle, { newTitle = it }, "タイトル")
            Spacer(Modifier.height(8.dp))
            Field(newUrl, { newUrl = it }, "https://…")
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(SURFACE_2)
                    .clickable {
                        val url = newUrl.trim()
                        if (url.startsWith("http")) {
                            Config.addCustomTile(context, newTitle, url)
                            order = Config.allTilesOrdered(context)
                            newTitle = ""
                            newUrl = ""
                            bump++
                        } else {
                            toast("URL は http から始めてください")
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                Text("リンクを追加", color = PRIMARY, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    @Composable
    private fun SettingLabel(text: String) {
        Spacer(Modifier.height(28.dp))
        Text(text, color = MUTED, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(9.dp))
    }

    @Composable
    private fun Field(
        value: String,
        onChange: (String) -> Unit,
        placeholder: String = "",
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            placeholder = { Text(placeholder, color = MUTED) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SURFACE,
                unfocusedContainerColor = SURFACE,
                focusedTextColor = PRIMARY,
                unfocusedTextColor = PRIMARY,
                cursorColor = PRIMARY,
                focusedIndicatorColor = STROKE,
                unfocusedIndicatorColor = STROKE,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    @Composable
    private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) PRIMARY else SURFACE)
                .clickable { onClick() }
                .padding(horizontal = 13.dp, vertical = 8.dp),
        ) {
            Text(
                text,
                color = if (selected) BG else SECONDARY,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }

    @Composable
    private fun ReorderButton(label: String, enabled: Boolean, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(SURFACE)
                .clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = if (enabled) PRIMARY else STROKE, fontSize = 13.sp)
        }
    }
}
