package com.masakasakasama.home.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class ReleaseInfo(
    /** Numeric version parsed from a tag like "v3" -> 3. 0 when unknown. */
    val versionCode: Int,
    val tag: String,
    val apkUrl: String,
)

object GitHubReleaseClient {

    /**
     * Resolves GitHub's public /releases/latest redirect instead of using the
     * rate-limited REST API. Home release asset names are deterministic.
     */
    suspend fun latestRelease(owner: String, repo: String): ReleaseInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("https://github.com/$owner/$repo/releases/latest")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Home-Launcher")
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                try {
                    if (conn.responseCode !in 200..399) return@runCatching null
                    releaseFromRedirect(owner, repo, conn.url.toString())
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        }

    internal fun releaseFromRedirect(
        owner: String,
        repo: String,
        redirectUrl: String,
    ): ReleaseInfo? {
        val uri = runCatching { URI(redirectUrl) }.getOrNull() ?: return null
        if (!uri.host.equals("github.com", ignoreCase = true)) return null
        val expectedPrefix = "/$owner/$repo/releases/tag/"
        if (!uri.path.startsWith(expectedPrefix, ignoreCase = true)) return null
        val tag = uri.path.substring(expectedPrefix.length).trim('/')
        val versionCode = parseVersionCode(tag)
        if (versionCode <= 0) return null
        val versionName = "1.0.$versionCode"
        return ReleaseInfo(
            versionCode = versionCode,
            tag = tag,
            apkUrl = "https://github.com/$owner/$repo/releases/download/" +
                "$tag/Home-$versionName.apk",
        )
    }

    /** "v12" -> 12, "v1.2.0" -> 0 (unknown). */
    internal fun parseVersionCode(tag: String): Int =
        tag.removePrefix("v").trim().toIntOrNull() ?: 0
}
