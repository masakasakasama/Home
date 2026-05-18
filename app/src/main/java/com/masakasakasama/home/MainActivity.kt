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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.masakasakasama.home.BuildConfig
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

private val BG = Color(0xFF000000)
private val DIVIDER = Color(0xFF1C1C1E)
private val LABEL = Color(0xFF6E6E73)
private val SUB = Color(0xFF8E8E93)
private val HI = Color(0xFFEDEDED)
private val UP = Color(0xFF32D74B)
private val DOWN = Color(0xFFFF453A)

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
        val (cs, _) = Config.cachedStock(this)
        quotes = cs.map { Quote(it.first, it.second, it.third) }
        val (items, age, _) = Config.cachedNews(this)
        news = NewsFeed(items, age)
        statuses = tiles.mapNotNull { t ->
            Config.cachedStatus(this, t.id)?.let { t.id to it }
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
                    val q = StockLive.quotes(
                        applicationContext, Config.watchlist(applicationContext)
                    )
                    if (q.isNotEmpty()) {
                        quotes = q
                        Config.cacheStock(applicationContext, q.map {
                            Triple(it.symbol, it.price, it.changePct)
                        })
                    } else if (quotes.isEmpty()) ok = false
                }.onFailure { ok = false }

                runCatching {
                    val f = NewsLive.feed(Config.newsFeed(applicationContext))
                    if (f.items.isNotEmpty()) {
                        news = f
                        Config.cacheNews(applicationContext, f.items, f.ageMinutes)
                    } else if (news.items.isEmpty()) ok = false
                }.onFailure { ok = false }

                val freshStatuses = statuses.toMutableMap()
                tiles.filter { it.kind == TileKind.WEB }.forEach { t ->
                    val su = t.statusUrl ?: return@forEach
                    runCatching {
                        WebStatusClient.fetch(su)?.let { s ->
                            freshStatuses[t.id] = s
                            Config.cacheStatus(applicationContext, t.id, s)
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

    private fun checkSelfUpdate() {
        lifecycleScope.launch {
            val latest = GitHubReleaseClient.latestRelease(
                AppCatalog.SELF_OWNER, AppCatalog.SELF_REPO
            ) ?: return@launch
            if (latest.versionCode > BuildConfig.VERSION_CODE) selfUpdate = latest
        }
    }

    private fun openTile(tile: Tile) {
        when (tile.kind) {
            TileKind.STOCK -> {
                val pkg = tile.pkg
                val launch = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
                if (launch != null) startActivity(launch)
                else toast("${tile.title} がインストールされていません")
            }
            else -> {
                val url = tile.url ?: return
                runCatching {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure { toast("開けませんでした") }
            }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // ---- Dashboard ----------------------------------------------------

    @Composable
    private fun Dashboard() {
        val context = LocalContext.current
        val update = selfUpdate
        var downloadStarted by remember { mutableStateOf(false) }
        var updateStatus by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(update?.tag) {
            if (update == null || downloadStarted) return@LaunchedEffect
            if (!ApkInstaller.canInstall(context)) return@LaunchedEffect
            downloadStarted = true
            updateStatus = "DOWNLOADING ${update.tag}"
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Home", color = Color.White, fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = when {
                            updateStatus != null -> updateStatus!!
                            update != null -> "UPDATE ${update.tag} ・ v${BuildConfig.VERSION_NAME}"
                            refreshing -> "更新中…"
                            !liveOk && lastSync > 0L -> "オフライン ・ 前回 ${clock(lastSync)}"
                            lastSync > 0L -> "最終更新 ${clock(lastSync)}"
                            else -> "v${BuildConfig.VERSION_NAME}"
                        },
                        color = when {
                            update != null -> UP
                            !liveOk && lastSync > 0L -> DOWN
                            else -> LABEL
                        },
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                    )
                }
                HeaderButton(if (refreshing) "···" else "↻") { refresh() }
                Spacer(Modifier.width(8.dp))
                HeaderButton("⚙") { showSettings = true }
            }
            Hairline()

            if (update != null && !ApkInstaller.canInstall(context)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { ApkInstaller.requestInstallPermission(context) }
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "新しいバージョン (${update.tag})：インストール許可が必要",
                        color = HI, fontSize = 13.sp, modifier = Modifier.weight(1f),
                    )
                    Text("許可 ›", color = UP, fontSize = 13.sp)
                }
                Hairline()
            }

            if (tiles.isEmpty()) {
                Text(
                    "表示するタイルがありません。⚙ から追加してください。",
                    color = SUB, fontSize = 13.sp,
                    modifier = Modifier.padding(22.dp),
                )
            }

            tiles.forEachIndexed { i, tile ->
                val no = "%02d".format(i + 1)
                when (tile.kind) {
                    TileKind.STOCK -> StockSection(no, tile)
                    TileKind.NEWS -> NewsSection(no, tile)
                    TileKind.FITNESS -> FitnessSection(no, tile)
                    TileKind.WEB -> WebSection(no, tile, statuses[tile.id])
                }
                Hairline()
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    private fun clock(ms: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

    @Composable
    private fun HeaderButton(label: String, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .border(1.dp, DIVIDER, CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = HI, fontSize = 15.sp)
        }
    }

    @Composable
    private fun Hairline() {
        Box(Modifier.fillMaxWidth().height(1.dp).background(DIVIDER))
    }

    @Composable
    private fun SectionHeader(no: String, tile: Tile, status: @Composable () -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$no  ${tile.title}  /  ${tile.category}",
                color = LABEL,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f),
            )
            status()
        }
        Spacer(Modifier.height(20.dp))
    }

    private fun changeColor(p: Double) = when {
        p > 0 -> UP
        p < 0 -> DOWN
        else -> SUB
    }

    private fun changeText(p: Double): String {
        val arrow = if (p >= 0) "▲" else "▼"
        val sign = if (p >= 0) "+" else "-"
        return "$arrow $sign%.2f %%".format(kotlin.math.abs(p))
    }

    @Composable
    private fun SectionBody(onClick: () -> Unit, content: @Composable () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 22.dp, vertical = 26.dp),
        ) { content() }
    }

    @Composable
    private fun StockSection(no: String, tile: Tile) {
        SectionBody({ openTile(tile) }) {
            SectionHeader(no, tile) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(6.dp).clip(CircleShape)
                            .background(if (quotes.isEmpty()) SUB else UP)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (quotes.isEmpty()) "OFFLINE"
                        else if (!liveOk) "CACHED" else "LIVE",
                        color = if (quotes.isEmpty()) SUB
                        else if (!liveOk) SUB else UP,
                        fontSize = 11.sp, letterSpacing = 2.sp,
                    )
                }
            }
            if (quotes.isEmpty()) {
                Text(
                    "ウィジェット未連携、または株価を取得できません",
                    color = SUB, fontSize = 13.sp,
                )
                return@SectionBody
            }
            val head = quotes.first()
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(StockLive.label(head.symbol), color = SUB, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        StockLive.formatPrice(head.price),
                        color = Color.White,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
                Text(
                    changeText(head.changePct),
                    color = changeColor(head.changePct),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            quotes.drop(1).forEach { q ->
                Spacer(Modifier.height(18.dp))
                Hairline()
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        StockLive.label(q.symbol), color = HI, fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(StockLive.formatPrice(q.price), color = Color.White, fontSize = 17.sp)
                    Spacer(Modifier.width(18.dp))
                    Text(
                        changeText(q.changePct),
                        color = changeColor(q.changePct),
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }

    @Composable
    private fun NewsSection(no: String, tile: Tile) {
        SectionBody({ openTile(tile) }) {
            SectionHeader(no, tile) {
                Text(
                    NewsLive.ago(news.ageMinutes),
                    color = LABEL, fontSize = 11.sp, letterSpacing = 2.sp,
                )
            }
            if (news.items.isEmpty()) {
                Text("目立ったニュースはありません", color = SUB, fontSize = 13.sp)
                return@SectionBody
            }
            news.items.forEachIndexed { i, h ->
                if (i > 0) {
                    Spacer(Modifier.height(16.dp))
                    Hairline()
                    Spacer(Modifier.height(16.dp))
                }
                Row {
                    Text(
                        "%02d".format(i + 1),
                        color = LABEL, fontSize = 12.sp, letterSpacing = 1.sp,
                        modifier = Modifier.padding(end = 14.dp, top = 2.dp),
                    )
                    Text(
                        h, color = Color.White, fontSize = 16.sp, lineHeight = 22.sp,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    @Composable
    private fun FitnessSection(no: String, tile: Tile) {
        val days = Config.trainDays(this)
        SectionBody({ openTile(tile) }) {
            SectionHeader(no, tile) {}
            val today = FitnessTip.today()
            Row(modifier = Modifier.fillMaxWidth()) {
                FitnessTip.week.forEach { d ->
                    val train = d.value in days
                    val isToday = d == today
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.width(20.dp).height(2.dp)
                                .background(if (isToday) Color.White else Color.Transparent)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            FitnessTip.jp(d),
                            color = if (train) Color.White else SUB,
                            fontSize = 17.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier
                                .size(if (train) 7.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        train -> Color.White
                                        isToday -> SUB
                                        else -> Color(0xFF2C2C2E)
                                    }
                                )
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Hairline()
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("今週", color = LABEL, fontSize = 11.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "週${days.size}回",
                        color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("次回", color = LABEL, fontSize = 11.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        FitnessTip.nextShort(days),
                        color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light,
                    )
                }
            }
        }
    }

    @Composable
    private fun WebSection(no: String, tile: Tile, status: WebStatus?) {
        SectionBody({ openTile(tile) }) {
            SectionHeader(no, tile) {}
            if (status != null && (status.primary.isNotEmpty() || status.detail.isNotEmpty())) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            status.detail.ifBlank {
                                (tile.url?.let { Uri.parse(it).host }) ?: "開く"
                            },
                            color = SUB, fontSize = 13.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (status.primary.isNotEmpty()) {
                        Spacer(Modifier.width(16.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                status.primary, color = Color.White,
                                fontSize = 34.sp, fontWeight = FontWeight.Light,
                            )
                            if (status.label.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    status.label, color = LABEL,
                                    fontSize = 11.sp, letterSpacing = 2.sp,
                                    modifier = Modifier.padding(bottom = 6.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (tile.url?.let { Uri.parse(it).host }) ?: "開く",
                        color = SUB, fontSize = 13.sp, modifier = Modifier.weight(1f),
                    )
                    Text("OPEN", color = LABEL, fontSize = 11.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("›", color = SUB, fontSize = 20.sp)
                }
            }
        }
    }

    // ---- Settings -----------------------------------------------------

    @Composable
    private fun SettingsScreen() {
        val ctx = this
        var feed by remember { mutableStateOf(Config.newsFeed(ctx)) }
        var watch by remember {
            mutableStateOf(Config.watchlist(ctx).joinToString(", "))
        }
        var refreshMin by remember { mutableStateOf(Config.refreshMin(ctx)) }
        var dayState by remember { mutableStateOf(Config.trainDays(ctx)) }
        var order by remember { mutableStateOf(Config.allTilesOrdered(ctx)) }
        var hidden by remember {
            mutableStateOf(order.filter { Config.isHidden(ctx, it.id) }.map { it.id }.toSet())
        }
        var newTitle by remember { mutableStateOf("") }
        var newUrl by remember { mutableStateOf("") }
        var bump by remember { mutableStateOf(0) }

        fun persistAndClose() {
            Config.setNewsFeed(ctx, feed)
            Config.setWatchlist(ctx, watch.split(",").map { it.trim() }.filter { it.isNotEmpty() })
            Config.setRefreshMin(ctx, refreshMin)
            Config.setTrainDays(ctx, dayState)
            Config.setOrder(ctx, order.map { it.id })
            order.forEach { Config.setHidden(ctx, it.id, it.id in hidden) }
            showSettings = false
            reloadConfig()
            refresh()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("設定", color = Color.White, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, DIVIDER, RoundedCornerShape(50))
                        .clickable { persistAndClose() }
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) { Text("保存して閉じる", color = HI, fontSize = 13.sp) }
            }

            SettingLabel("ニュース RSS フィード")
            Field(feed, { feed = it })

            SettingLabel("株 ウォッチリスト（カンマ区切り・空=株アプリ準拠）")
            Field(watch, { watch = it })

            SettingLabel("更新間隔（分）")
            Row {
                listOf(15, 30, 60).forEach { m ->
                    Chip("$m", refreshMin == m) { refreshMin = m }
                    Spacer(Modifier.width(8.dp))
                }
            }

            SettingLabel("トレーニング曜日")
            Row {
                DayOfWeek.values().forEach { d ->
                    val on = d.value in dayState
                    Chip(FitnessTip.jp(d), on) {
                        dayState = if (on) dayState - d.value else dayState + d.value
                    }
                    Spacer(Modifier.width(6.dp))
                }
            }

            SettingLabel("タイルの表示・並び順")
            bump.let {
                order.forEachIndexed { i, t ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${t.emoji}  ${t.title}", color = HI, fontSize = 15.sp,
                            modifier = Modifier.weight(1f))
                        ReorderBtn("▲", i > 0) {
                            val m = order.toMutableList()
                            val tmp = m[i]; m[i] = m[i - 1]; m[i - 1] = tmp
                            order = m; bump++
                        }
                        Spacer(Modifier.width(4.dp))
                        ReorderBtn("▼", i < order.size - 1) {
                            val m = order.toMutableList()
                            val tmp = m[i]; m[i] = m[i + 1]; m[i + 1] = tmp
                            order = m; bump++
                        }
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = t.id !in hidden,
                            onCheckedChange = { vis ->
                                hidden = if (vis) hidden - t.id else hidden + t.id
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = UP,
                            ),
                        )
                        if (Config.isCustom(t.id)) {
                            Spacer(Modifier.width(8.dp))
                            ReorderBtn("✕", true) {
                                Config.removeCustomTile(ctx, t.id)
                                order = order.filter { it.id != t.id }
                                bump++
                            }
                        }
                    }
                }
            }

            SettingLabel("カスタムリンクを追加")
            Field(newTitle, { newTitle = it }, "タイトル")
            Spacer(Modifier.height(8.dp))
            Field(newUrl, { newUrl = it }, "https://…")
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x1FFFFFFF))
                    .clickable {
                        val u = newUrl.trim()
                        if (u.startsWith("http")) {
                            Config.addCustomTile(ctx, newTitle, u)
                            order = Config.allTilesOrdered(ctx)
                            newTitle = ""; newUrl = ""; bump++
                        } else toast("URL は http から始めてください")
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) { Text("追加", color = HI, fontSize = 14.sp) }

            Spacer(Modifier.height(40.dp))
        }
    }

    @Composable
    private fun SettingLabel(text: String) {
        Spacer(Modifier.height(26.dp))
        Text(text, color = LABEL, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))
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
            placeholder = { Text(placeholder, color = SUB) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0x12FFFFFF),
                unfocusedContainerColor = Color(0x12FFFFFF),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedIndicatorColor = SUB,
                unfocusedIndicatorColor = DIVIDER,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    @Composable
    private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (on) UP.copy(alpha = 0.25f) else Color(0x12FFFFFF))
                .border(
                    1.dp,
                    if (on) UP else DIVIDER,
                    RoundedCornerShape(50),
                )
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            Text(text, color = if (on) Color.White else SUB, fontSize = 13.sp)
        }
    }

    @Composable
    private fun ReorderBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, DIVIDER, RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = if (enabled) HI else Color(0xFF3A3A3C), fontSize = 13.sp)
        }
    }
}
