package com.masakasakasama.home.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AiGateway(val url: String, val token: String)

/**
 * No DeepSeek key lives in the APK. The gateway credential is encrypted with an
 * AndroidKeyStore non-exportable key and saved under noBackupFilesDir.
 */
object AiGatewaySettings {
    private const val KEY_ALIAS = "home_ai_proxy_v1"
    private const val FILE_NAME = "ai_gateway_v1.json"

    fun isValidUrl(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            uri.query == null &&
            uri.fragment == null &&
            uri.path == "/ask" &&
            !uri.host.equals("api.deepseek.com", ignoreCase = true)
    }.getOrDefault(false)

    private fun file(context: Context): File =
        File(context.applicationContext.noBackupFilesDir, FILE_NAME)

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun load(context: Context): AiGateway? {
        val stored = file(context)
        if (!stored.exists()) return null
        return runCatching {
            val raw = JSONObject(stored.readText(Charsets.UTF_8))
            val iv = Base64.decode(raw.getString("iv"), Base64.NO_WRAP)
            val encrypted = Base64.decode(raw.getString("ciphertext"), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            val data = JSONObject(String(cipher.doFinal(encrypted), Charsets.UTF_8))
            val gateway = AiGateway(data.getString("url"), data.getString("token"))
            gateway.takeIf { isValidUrl(it.url) && it.token.length >= 32 }
        }.getOrNull()
    }

    fun save(context: Context, url: String, token: String) {
        require(isValidUrl(url)) { "HTTPS の中継URL（/ask）が必要です" }
        require(token.length >= 32 && !token.contains('\n')) {
            "中継トークンは32文字以上にしてください"
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = JSONObject()
            .put("url", url.trim())
            .put("token", token)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val data = JSONObject()
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(cipher.doFinal(payload), Base64.NO_WRAP))
            .toString()
        val target = file(context)
        val temp = File(target.parentFile, "$FILE_NAME.tmp")
        temp.writeText(data, Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IllegalStateException("中継設定を保存できませんでした")
        }
    }

    fun clear(context: Context) { file(context).delete() }
}
