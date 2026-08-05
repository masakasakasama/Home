package com.masakasakasama.home.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubReleaseClientTest {

    @Test
    fun buildsReleaseInfoFromLatestRedirect() {
        val release = GitHubReleaseClient.releaseFromRedirect(
            owner = "masakasakasama",
            repo = "Home",
            redirectUrl = "https://github.com/masakasakasama/Home/releases/tag/v26",
        )

        assertEquals(26, release?.versionCode)
        assertEquals("v26", release?.tag)
        assertEquals(
            "https://github.com/masakasakasama/Home/releases/download/v26/Home-1.0.26.apk",
            release?.apkUrl,
        )
    }

    @Test
    fun rejectsUnexpectedRedirectsAndSemanticTags() {
        assertNull(
            GitHubReleaseClient.releaseFromRedirect(
                "masakasakasama",
                "Home",
                "https://example.com/masakasakasama/Home/releases/tag/v26",
            )
        )
        assertNull(
            GitHubReleaseClient.releaseFromRedirect(
                "masakasakasama",
                "Home",
                "https://github.com/masakasakasama/Home/releases/tag/v1.2.0",
            )
        )
    }
}
