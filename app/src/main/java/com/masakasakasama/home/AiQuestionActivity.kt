package com.masakasakasama.home

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.masakasakasama.home.ai.AiGateway
import com.masakasakasama.home.ai.AiGatewayClient
import com.masakasakasama.home.ai.AiGatewaySettings
import com.masakasakasama.home.ai.PrivateQuestionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val AI_BG = Color(0xFF070809)
private val AI_TEXT = Color(0xFFF5F7FA)
private val AI_MUTED = Color(0xFFABB3C0)
private val AI_ERROR = Color(0xFFFF777D)

class AiQuestionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { QuestionScreen() }
        }
    }

    @Composable
    private fun QuestionScreen() {
        var gateway by remember { mutableStateOf(AiGatewaySettings.load(this)) }
        var setup by remember { mutableStateOf(gateway == null) }
        var endpoint by remember { mutableStateOf(gateway?.url ?: "") }
        // Do not populate credential text or persist it in Compose saved state.
        var newToken by remember { mutableStateOf("") }
        var question by remember { mutableStateOf("") }
        var answer by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        var pending by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AI_BG)
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { finish() }) { Text("← Home") }
                Spacer(Modifier.weight(1f))
                if (!setup) TextButton(onClick = { setup = true; error = "" }) {
                    Text("接続設定")
                }
            }
            Text("AI質問", color = AI_TEXT, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("DeepSeek V4.1 Flash ｜ 一般的な質問専用", color = AI_MUTED, fontSize = 12.sp)
            Text(
                "外部送信するのは入力した質問のみ。住所、認証情報、家電操作、" +
                    "健康情報、金融情報などは送信しないでください。" +
                    "入力内容はDeepSeekのサーバーで処理されます。",
                color = AI_MUTED,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )

            if (setup) {
                Text("中継サーバー設定", color = AI_TEXT, fontSize = 19.sp)
                Text(
                    "DeepSeekのAPIキーはここに入力しません。" +
                        "自分で管理する認証付きHTTPS中継サーバーが必要です。",
                    color = AI_MUTED,
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it; error = "" },
                    label = { Text("HTTPS中継URL") },
                    placeholder = { Text("https://your-domain.example/ask") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = newToken,
                    onValueChange = { newToken = it; error = "" },
                    label = { Text("中継用アクセストークン（32文字以上）") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Button(
                    onClick = {
                        runCatching {
                            AiGatewaySettings.save(this@AiQuestionActivity, endpoint, newToken)
                            AiGatewaySettings.load(this@AiQuestionActivity)
                                ?: error("保存した設定を読み込めませんでした")
                        }.onSuccess {
                            gateway = it
                            newToken = ""
                            error = ""
                            setup = false
                        }.onFailure {
                            error = it.message ?: "設定の保存に失敗しました"
                        }
                    },
                    enabled = AiGatewaySettings.isValidUrl(endpoint) && newToken.length >= 32,
                ) { Text("安全に保存") }
                if (gateway != null) {
                    TextButton(onClick = {
                        AiGatewaySettings.clear(this@AiQuestionActivity)
                        gateway = null
                        answer = ""
                        error = ""
                        newToken = ""
                    }) { Text("接続情報を削除", color = AI_ERROR) }
                    TextButton(onClick = { setup = false; error = "" }) { Text("戻る") }
                }
            } else {
                OutlinedTextField(
                    value = question,
                    onValueChange = { if (it.length <= 1600) { question = it; error = "" } },
                    label = { Text("一般的な質問") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 7,
                )
                val blocked = PrivateQuestionPolicy.blocked(question)
                if (blocked) {
                    Text("個人情報または家電操作に関する内容は送信禁止", color = AI_ERROR)
                }
                Button(
                    onClick = {
                        val activeGateway: AiGateway = gateway ?: return@Button
                        val text = question.trim()
                        pending = true
                        answer = ""
                        error = ""
                        lifecycleScope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    AiGatewayClient.ask(activeGateway, text)
                                }
                            }.onSuccess { answer = it }
                                .onFailure { error = it.message ?: "通信に失敗しました" }
                            pending = false
                        }
                    },
                    enabled = !pending && question.isNotBlank() && question.length <= 1600 &&
                        !blocked && gateway != null,
                    colors = ButtonDefaults.buttonColors(),
                ) { Text(if (pending) "回答を取得中…" else "質問を送信") }
                if (pending) CircularProgressIndicator()
                if (answer.isNotBlank()) {
                    Text("回答", color = AI_MUTED, fontWeight = FontWeight.SemiBold)
                    Text(answer, color = AI_TEXT, lineHeight = 23.sp)
                }
                Text(
                    "会話履歴、家のデバイス情報、位置情報、他アプリのデータは送信しません。" +
                        "家電操作機能とは接続していません。",
                    color = AI_MUTED,
                    fontSize = 12.sp,
                )
            }
            if (error.isNotBlank()) Text(error, color = AI_ERROR, fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
        }
    }
}
