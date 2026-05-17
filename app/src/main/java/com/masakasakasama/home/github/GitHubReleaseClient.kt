package com.masakasakasama.home.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    /** Numeric version parsed from a tag like "v3" -> 3. 0 when unknown. */
    val versionCode: Int,
    val tag: String,
    val apkUrl: String,
)

object GitHubReleaseClient {

    /**
     * Fetches the latest GitHub release for [owner]/[repo] and returns the
     * first asset whose name ends with .apk. Returns null when there is no
     * release, no apk asset, or the network call fails.
     */
    suspend fun latestRelease(owner: String, repo: String): ReleaseInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Home-Launcher")
                    connectTimeout = 15000
                    readTimeout = 15000
                }
                if (conn.responseCode != 200) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)

                val tag = json.optString("tag_name")
                val assets = json.optJSONArray("assets") ?: return@runCatching null
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
                if (apkUrl.isNullOrEmpty()) return@runCatching null

                ReleaseInfo(
                    versionCode = parseVersionCode(tag),
                    tag = tag,
                    apkUrl = apkUrl,
                )
            }.getOrNull()
        }

    /** "v12" -> 12, "v1.2.0" -> 0 (unknown). */
    private fun parseVersionCode(tag: String): Int =
        tag.removePrefix("v").trim().toIntOrNull() ?: 0
}
