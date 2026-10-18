package com.masakasakasama.home.data

/** Home updates itself from this repo's GitHub Releases. */
object AppCatalog {
    const val SELF_OWNER = "masakasakasama"
    const val SELF_REPO = "Home" // Old publicly distributed installation; retained as fallback
    const val PUBLIC_RELEASE_REPO = "Home-Downloads"
    val UPDATE_REPOS = listOf(PUBLIC_RELEASE_REPO, SELF_REPO)
}
