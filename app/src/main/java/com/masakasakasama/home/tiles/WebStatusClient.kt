package com.masakasakasama.home.tiles

import com.masakasakasama.home.data.WebStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads an optional `status.json` a web app can publish next to its
 * index page, e.g. {"primary":"4","label":"OPEN","detail":"今日 原稿"}.
 * Every field is optional; a missing file just yields null.
 */
object WebStatusClient {

    suspend fun fetch(statusUrl: String): WebStatus? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(statusUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Home-Launcher")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != 200) return@runCatching null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(body)
            val s = WebStatus(
                primary = o.optString("primary").trim(),
                label = o.optString("label").trim(),
                detail = o.optString("detail").trim(),
            )
            if (s.primary.isEmpty() && s.label.isEmpty() && s.detail.isEmpty()) null else s
        }.getOrNull()
    }
}
