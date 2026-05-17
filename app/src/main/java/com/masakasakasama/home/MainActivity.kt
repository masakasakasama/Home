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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.masakasakasama.home.stock.Quote
import com.masakasakasama.home.stock.StockLive
import com.masakasakasama.home.tiles.FitnessTip
import com.masakasakasama.home.tiles.NewsFeed
import com.masakasakasama.home.tiles.NewsLive
import kotlinx.coroutines.launch

private val BG = Color(0xFF000000)
private val DIVIDER = Color(0xFF1C1C1E)
private val LABEL = Color(0xFF6E6E73)
private val SUB = Color(0xFF8E8E93)
private val HI = Color(0xFFEDEDED)
private val UP = Color(0xFF32D74B)
private val DOWN = Color(0xFFFF453A)

class MainActivity : ComponentActivity() {

    private var selfUpdate by mutableStateOf<ReleaseInfo?>(null)
    private var stockQuotes by mutableStateOf<List<Quote>>(emptyList())
    private var news by mutableStateOf(NewsFeed(emptyList(), null))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.fillMaxSize().background(BG)) { HomeScreen() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkSelfUpdate()
        refreshStocks()
        refreshNews()
    }

    private fun refreshStocks() {
        lifecycleScope.launch {
            val q = StockLive.quotes(applicationContext)
            if (q.isNotEmpty()) stockQuotes = q
        }
    }

    private fun refreshNews() {
        lifecycleScope.launch {
            val f = NewsLive.feed()
            if (f.items.isNotEmpty()) news = f
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

    @Composable
    private fun HomeScreen() {
        val context = LocalContext.current
        val update = selfUpdate
        var updateStatus by remember { mutableStateOf<String?>(null) }
        var downloadStarted by remember { mutableStateOf(false) }

        fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

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
                    if (launch != null) context.startActivity(launch)
                    else toast("${app.title} がインストールされていません")
                }
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
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Home", color = Color.White, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    text = updateStatus
                        ?: (if (update != null) "UPDATE ${update.tag}"
                            else "v${BuildConfig.VERSION_NAME}"),
                    color = if (update != null) UP else LABEL,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                )
            }
            Hairline()

            AppCatalog.apps.forEachIndexed { i, app ->
                val no = "%02d".format(i + 1)
                when {
                    app.target is Target.InstalledApp ->
                        StockSection(no, app, stockQuotes) { openApp(app) }
                    app.category == "BBC WORLD" ->
                        NewsSection(no, app, news) { openApp(app) }
                    app.title == "フィットネス" ->
                        FitnessSection(no, app) { openApp(app) }
                    else -> WebSection(no, app) { openApp(app) }
                }
                Hairline()
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    @Composable
    private fun Hairline() {
        Box(Modifier.fillMaxWidth().height(1.dp).background(DIVIDER))
    }

    @Composable
    private fun SectionHeader(no: String, app: AppEntry, status: @Composable () -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$no  ${app.title}  /  ${app.category}",
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
    private fun StockSection(
        no: String,
        app: AppEntry,
        quotes: List<Quote>,
        onClick: () -> Unit,
    ) {
        SectionBody(onClick) {
            SectionHeader(no, app) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(6.dp).clip(CircleShape)
                            .background(if (quotes.isEmpty()) SUB else UP)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (quotes.isEmpty()) "OFFLINE" else "LIVE",
                        color = if (quotes.isEmpty()) SUB else UP,
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
                    Text(StockLive.label(q.symbol), color = HI, fontSize = 15.sp,
                        modifier = Modifier.weight(1f))
                    Text(StockLive.formatPrice(q.price), color = Color.White,
                        fontSize = 17.sp)
                    Spacer(Modifier.width(20.dp))
                    Text(
                        changeText(q.changePct),
                        color = changeColor(q.changePct),
                        fontSize = 14.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(96.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun NewsSection(
        no: String,
        app: AppEntry,
        feed: NewsFeed,
        onClick: () -> Unit,
    ) {
        SectionBody(onClick) {
            SectionHeader(no, app) {
                Text(
                    NewsLive.ago(feed.ageMinutes),
                    color = LABEL, fontSize = 11.sp, letterSpacing = 2.sp,
                )
            }
            if (feed.items.isEmpty()) {
                Text("目立ったニュースはありません", color = SUB, fontSize = 13.sp)
                return@SectionBody
            }
            feed.items.forEachIndexed { i, h ->
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
    private fun FitnessSection(no: String, app: AppEntry, onClick: () -> Unit) {
        SectionBody(onClick) {
            SectionHeader(no, app) {}
            val today = FitnessTip.today()
            Row(modifier = Modifier.fillMaxWidth()) {
                FitnessTip.week.forEach { d ->
                    val train = d in FitnessTip.trainDays
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
                        "週${FitnessTip.perWeek}回",
                        color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("次回", color = LABEL, fontSize = 11.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        FitnessTip.nextShort(),
                        color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light,
                    )
                }
            }
        }
    }

    @Composable
    private fun WebSection(no: String, app: AppEntry, onClick: () -> Unit) {
        SectionBody(onClick) {
            SectionHeader(no, app) {}
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (app.target as? Target.Web)?.let { Uri.parse(it.url).host } ?: "開く",
                    color = SUB, fontSize = 13.sp, modifier = Modifier.weight(1f),
                )
                Text("OPEN", color = LABEL, fontSize = 11.sp, letterSpacing = 2.sp)
                Spacer(Modifier.width(10.dp))
                Text("›", color = SUB, fontSize = 20.sp)
            }
        }
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
}
