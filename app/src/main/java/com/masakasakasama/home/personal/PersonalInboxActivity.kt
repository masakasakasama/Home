package com.masakasakasama.home.personal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.masakasakasama.home.tiles.NewsLive
import com.masakasakasama.home.data.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Local opt-in Gmail update counters; never store sender, subject or body. */
object GmailUpdates {
    private const val PREF = "personal_gmail_update_counts"
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    fun count(context: Context): Int {
        val p = prefs(context)
        return if (p.getString("day", "") == LocalDate.now().toString()) p.getInt("count", 0) else 0
    }
    fun lastAt(context: Context): Long = prefs(context).getLong("last", 0L)
    @Synchronized fun record(context: Context, key: String) {
        val p = prefs(context)
        val day = LocalDate.now().toString()
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val seen = if (p.getString("day", "") == day)
            p.getStringSet("seen", emptySet())?.toMutableSet() ?: mutableSetOf()
        else mutableSetOf()
        if (!seen.add(hash)) return
        // Limit the hash cache (and notifications to one day) without persisting metadata.
        if (seen.size > 200) seen.clear()
        seen.add(hash)
        val next = (if (p.getString("day", "") == day) p.getInt("count", 0) else 0) + 1
        p.edit().putString("day", day).putInt("count", next)
            .putLong("last", System.currentTimeMillis()).putStringSet("seen", seen).apply()
    }
}

/** Keystore AES/GCM encrypts the existing Home Backend owner token at rest. */
object PersonalToken {
    private const val PREF = "personal_home_secrets"
    private const val ALIAS = "personal_home_backend_token"
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    fun load(context: Context): String = runCatching {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val iv = Base64.decode(p.getString("iv", ""), Base64.NO_WRAP)
        val ciphertext = Base64.decode(p.getString("ciphertext", ""), Base64.NO_WRAP)
        if (iv.size != 12 || ciphertext.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrDefault("")
    fun save(context: Context, token: String) {
        if (token.isBlank()) {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
            return
        }
        require(token.none { it.isWhitespace() } && token.length <= 512)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("ciphertext", Base64.encodeToString(
                cipher.doFinal(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
            .apply()
    }
}

data class BrainHeadline(val score: Int, val summary: String, val url: String)
data class BrainDigest(val at: String, val digest: String, val headlines: List<BrainHeadline>)

object BrainInbox {
    const val ENDPOINT = "https://ai-assistant-handmade.vercel.app/api/news"
    suspend fun fetch(token: String): List<BrainDigest> = withContext(Dispatchers.IO) {
        require(token.isNotBlank() && token.none { it.isWhitespace() }) { "Backend認証トークンが未設定です" }
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 12000
            readTimeout = 12000
        }
        try {
            if (conn.responseCode != 200) error("ニュースの取得に失敗 (HTTP ${conn.responseCode})")
            val data = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val rows = data.optJSONArray("items") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until minOf(12, rows.length())) {
                    val row = rows.optJSONObject(i) ?: continue
                    val alerts = row.optJSONArray("alerts")
                    val headlines = buildList {
                        if (alerts != null) for (j in 0 until minOf(5, alerts.length())) {
                            val a = alerts.optJSONObject(j) ?: continue
                            val url = a.optString("url")
                            if (!url.startsWith("https://") && !url.startsWith("http://")) continue
                            add(BrainHeadline(a.optInt("score"), a.optString("summary"), url))
                        }
                    }
                    add(BrainDigest(row.optString("capturedAt"),row.optString("digest"),headlines))
                }
            }
        } finally { conn.disconnect() }
    }
}

class PersonalInboxActivity : ComponentActivity() {
    private var news by mutableStateOf<List<BrainDigest>>(emptyList())
    private var loading by mutableStateOf(false)
    private var publicNews by mutableStateOf<List<String>>(emptyList())
    private var error by mutableStateOf<String?>(null)
    private var gmailCount by mutableStateOf(0)
    private var gmailLast by mutableStateOf(0L)
    private var tokenSaved by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenSaved = PersonalToken.load(this).isNotBlank()
        refresh()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { Screen() }
        }
    }
    override fun onResume() {
        super.onResume()
        gmailCount = getSharedPreferences("personal_gmail_update_counts", MODE_PRIVATE).getInt("unread_count", 0)
        gmailLast = getSharedPreferences("personal_gmail_update_counts", MODE_PRIVATE).getLong("unread_last", 0)
    }
    private val gmailAuthorization = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            runCatching { Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(result.data).accessToken }
                .onSuccess { token -> if (token != null) fetchGmailCount(token) }
                .onFailure { error = "Gmail認可に失敗しました。端末の保存データは保持しています。" }
        }
    }
    private fun authorizeGmail() {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/gmail.metadata"))).build()
        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    result.pendingIntent?.let { gmailAuthorization.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
                } else result.accessToken?.let { fetchGmailCount(it) }
            }
            .addOnFailureListener { error = "Gmailの接続設定・認可を確認してください。既存データは保持しています。" }
    }
    private fun fetchGmailCount(accessToken: String) {
        // Token is used only in memory. Never send Gmail data to the Home backend.
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val connection = (URL("https://gmail.googleapis.com/gmail/v1/users/me/labels/UNREAD?fields=messagesUnread").openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false; connectTimeout = 12000; readTimeout = 12000
                    setRequestProperty("Authorization", "Bearer $accessToken")
                }
                try {
                    check(connection.responseCode == 200)
                    JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getInt("messagesUnread")
                } finally { connection.disconnect() }
            } }.onSuccess { count ->
                gmailCount = count; gmailLast = System.currentTimeMillis()
                getSharedPreferences("personal_gmail_update_counts", MODE_PRIVATE).edit()
                    .putInt("unread_count", count).putLong("unread_last", gmailLast).apply()
                error = null
            }.onFailure { error = "Gmail取得失敗。オフラインの保存件数を保持しています。再認可して再試行できます。" }
        }
    }
    private fun refresh() {
        lifecycleScope.launch {
            val feed = NewsLive.feed(Config.newsFeed(applicationContext), n = 5)
            publicNews = feed.items
        }
        gmailCount = getSharedPreferences("personal_gmail_update_counts", MODE_PRIVATE).getInt("unread_count", 0)
        gmailLast = getSharedPreferences("personal_gmail_update_counts", MODE_PRIVATE).getLong("unread_last", 0)
        val token = PersonalToken.load(this)
        tokenSaved = token.isNotBlank()
        if (token.isBlank()) return
        loading = true
        lifecycleScope.launch {
            try {
                news = BrainInbox.fetch(token)
                error = null
            } catch (_: Exception) {
                error = "Brainのニュースを取得できません。認証トークン・PC側配信を確認"
            } finally { loading = false }
        }
    }

    @Composable private fun Screen() {
        var settingToken by remember { mutableStateOf("") }
        var showTokenSetting by remember { mutableStateOf(false) }
        val background = Color(0xFF070809)
        val surface = Color(0xFF181B20)
        Column(Modifier.fillMaxSize().background(background).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Text("個人の更新情報", style = MaterialTheme.typography.headlineMedium)
            Text("共有Homeには送信しない。Gmailの通知権限は無効化済み",
                color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            Text("Gmail 未読: $gmailCount 件（最終取得: ${if (gmailLast == 0L) "未取得" else java.util.Date(gmailLast).toString()}）")
            Text("Google OAuthの読み取り専用メタデータ権限で未読件数を取得します。本文・送信者・件名は取得しません。")
            Button(onClick = { authorizeGmail() }) { Text("個人用Gmailに接続・更新") }
            Surface(shape = RoundedCornerShape(20.dp), color = surface) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("公開ニュース（RSS）", fontWeight = FontWeight.Bold)
                    if (publicNews.isEmpty()) Text("読み込み中または取得できる記事なし")
                    publicNews.forEach { headline ->
                        Text("・" + headline, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = surface) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Brainの重要ニュース", fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f))
                        Text("更新", modifier = Modifier.clickable { refresh() }, color = Color(0xFF8AD0FF))
                    }
                    if (!tokenSaved) Text("未接続。下の認証設定を開いてください")
                    if (loading) Text("取得中…")
                    error?.let { Text(it, color = Color(0xFFFF8990)) }
                    if (tokenSaved && !loading && news.isEmpty() && error == null) {
                        Text("Brainからまだニュースが届いていません")
                    }
                    news.forEach { batch ->
                        Text(batch.at.take(16).replace("T", " "), color = Color.LightGray)
                        Text(batch.digest)
                        batch.headlines.forEach { story ->
                            Column(Modifier.clickable {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(story.url)))
                            }) {
                                Text("${story.score}/100  ${story.summary}",
                                    color = Color(0xFF8AD0FF))
                                Text("元記事を開く", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
            Button(onClick = { showTokenSetting = !showTokenSetting }) {
                Text(if (showTokenSetting) "設定を閉じる" else "Brain連携の認証設定")
            }
            if (showTokenSetting) {
                Text("既存Tatsu Home Backendの認証トークン。公開ニュースだけを取得する。端末のKeystoreで暗号化保存",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = settingToken, onValueChange = { settingToken = it },
                    label = { Text("Backend認証トークン") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    runCatching { PersonalToken.save(this@PersonalInboxActivity, settingToken.trim()) }
                        .onSuccess { settingToken = ""; showTokenSetting = false; refresh() }
                        .onFailure { error = "認証トークンを保存できません" }
                }) { Text("保存して取得") }
                Button(onClick = {
                    PersonalToken.save(this@PersonalInboxActivity, "")
                    tokenSaved = false; news = emptyList(); error = null
                }) { Text("認証情報を削除") }
            }
        }
    }
}
