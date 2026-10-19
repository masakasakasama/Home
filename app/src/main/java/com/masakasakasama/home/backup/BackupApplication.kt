package com.masakasakasama.home.backup

class BackupApplication : android.app.Application() {
    override fun onCreate() { super.onCreate(); LocalBackup.recover(this) }
}
