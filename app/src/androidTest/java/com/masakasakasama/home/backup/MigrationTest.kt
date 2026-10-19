package com.masakasakasama.home.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Disposable emulator fixture ONLY. Host saves encrypted backup before uninstalling QA package. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun prefs() = context.getSharedPreferences("home_config", 0)
    private val pass get() = "fixture-only-migration-passphrase".toCharArray()
    @Test fun exportFixture() {
        require(context.packageName.endsWith(".securityqa"))
        prefs().edit().clear().putString("symbols_0", "AAPL,7203.T")
            .putString("holdings_json", "[{\"symbol\":\"AAPL\",\"shares\":12.5,\"avgCost\":100.0}]")
            .putString("alerts_json", "[{\"symbol\":\"AAPL\",\"kind\":\"ABOVE\",\"value\":200,\"enabled\":true}]")
            .putString("custom_tiles", "[{\"id\":\"test\",\"title\":\"日本語\",\"url\":\"https://example.com\"}]")
            .putInt("tile_usage_test", 42).putLong("time", Long.MAX_VALUE)
            .putStringSet("seen", setOf("a", "b")).putBoolean("enabled", true)
            .putString("anthropic_api_key", "fixture-secret-never-export").commit()
        val before = LocalBackup.snapshot(context)
        assertFalse(before.contains("fixture-secret-never-export"))
        val bytes = BackupCrypto.encrypt(before.toByteArray(), pass)
        val file = File(context.filesDir, "migration.hbk"); file.writeBytes(bytes)
        assertEquals(before, String(BackupCrypto.decrypt(file.readBytes(), pass)))
        File(context.filesDir, "migration.sha256").writeText(hash(before))
    }
    @Test fun importFixture() {
        require(context.packageName.endsWith(".securityqa"))
        val restored = String(BackupCrypto.decrypt(File(context.filesDir, "migration.hbk").readBytes(), pass))
        assertEquals(File(context.filesDir, "migration.sha256").readText(), hash(restored))
        prefs().edit().putString("before_restore", "preserve-on-rollback").commit()
        val before = LocalBackup.snapshot(context)
        LocalBackup.restore(context, restored, before)
        assertEquals(hash(restored), hash(LocalBackup.snapshot(context)))
        assertEquals("AAPL,7203.T", prefs().getString("symbols_0", ""))
        assertEquals(42, prefs().getInt("tile_usage_test", 0))
        assertEquals("", prefs().getString("anthropic_api_key", ""))
        LocalBackup.rollback(context)
        assertEquals(before, LocalBackup.snapshot(context))
        LocalBackup.restore(context, restored, before)
        assertEquals(hash(restored), hash(LocalBackup.snapshot(context)))
    }
    @Test fun verifyWatchlistPermission() {
        require(context.packageName.endsWith(".securityqa"))
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,
            context.packageManager.checkPermission("com.example.stockwidget.READ_WATCHLIST", context.packageName))
        context.contentResolver.query(android.net.Uri.parse("content://com.example.stockwidget.watchlist/symbols"), null, null, null, null).use { cursor ->
            assertNotNull(cursor)
            assertTrue(cursor!!.count > 0)
        }
    }
    @Test fun verifyLegacyHostBackup() {
        require(context.packageName.endsWith(".securityqa"))
        val raw = String(BackupCrypto.decrypt(File(context.filesDir, "legacy.hbk").readBytes(), pass))
        val before = LocalBackup.snapshot(context)
        LocalBackup.restore(context, raw, before)
        assertEquals("AAPL,7203.T", prefs().getString("symbols_0", ""))
        assertEquals(Long.MAX_VALUE, prefs().getLong("time", 0L))
        assertEquals(42, prefs().getInt("tile_usage_test", 0))
        assertEquals(setOf("a", "b"), prefs().getStringSet("seen", emptySet()))
        LocalBackup.rollback(context)
        assertEquals(before, LocalBackup.snapshot(context))
    }
    private fun hash(raw: String) = java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
