package com.masakasakasama.home.ai

import org.json.JSONObject
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Sends only the explicit question. No history, app inventory, location,
 * device state, stock/health records or tool credentials are included.
 */
object AiGatewayClient {
    fun ask(gateway: AiGateway, question: String): String {
        require(AiGatewaySettings.isValidUrl(gateway.url)) { "無効な接続先です" }
        require(question.isNotBlank() && question.length <= 1600) {
            "質問は1600文字以内で入力してください"
        }
        require(!PrivateQuestionPolicy.blocked(question)) {
            "個人情報または家電操作に関する内容は送信できません"
        }
        val connection = URL(gateway.url).openConnection() as HttpsURLConnection
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 10_000
        connection.readTimeout = 35_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Authorization", "Bearer ${gateway.token}")
        connection.setRequestProperty("Cache-Control", "no-store")
        try {
            val payload = JSONObject().put("question", question.trim()).toString()
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode != 200) {
                throw IOException(
                    when (connection.responseCode) {
                        401 -> "認証失敗: 中継トークンを確認してください"
                        422 -> "個人情報または家電操作に関する内容は送信できません"
                        429 -> "利用回数の上限に達しました"
                        else -> "中継サーバーの応答エラー (${connection.responseCode})"
                    },
                )
            }
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return JSONObject(response).optString("answer").takeIf { it.isNotBlank() }
                ?: throw IOException("回答を取得できませんでした")
        } finally {
            connection.disconnect()
        }
    }
}

/** Defense in depth; the backend independently applies its own checks. */
object PrivateQuestionPolicy {
    private val forbidden = listOf(
        Regex("パスワード|暗証番号|認証コード|秘密鍵|API.?キー|トークン|口座番号|カード番号|住所|自宅の場所|正確な位置|居場所", RegexOption.IGNORE_CASE),
        Regex("(鍵|ドア|玄関|照明|エアコン|カメラ|スマートロック|家電).{0,18}(開け|閉め|つけ|消し|操作|解除|施錠|解錠|状態)", RegexOption.IGNORE_CASE),
        Regex("(開け|閉め|つけ|消し|操作|解除|施錠|解錠).{0,18}(鍵|ドア|玄関|照明|エアコン|カメラ|スマートロック|家電)", RegexOption.IGNORE_CASE),
        Regex("password|passcode|api.?key|access.?token|private.?key|bank.?account|home.?address|my.?location", RegexOption.IGNORE_CASE),
        Regex("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}"),
        Regex("sk-[A-Za-z0-9_-]{12,}"),
    )
    fun blocked(question: String): Boolean = forbidden.any { it.containsMatchIn(question) }
}
