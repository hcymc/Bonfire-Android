package cn.hcy.gopan

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay

/** 补全用户输入的服务器地址：缺协议时默认走 HTTPS，已是完整 URL 则原样保留。 */
internal fun normalizeServer(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    val lowered = trimmed.lowercase()
    return if (lowered.startsWith("http://") || lowered.startsWith("https://")) trimmed
    else "https://$trimmed"
}

private val EMAIL_RE = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

@Composable
internal fun Welcome(s: DriveState) {
    var server by remember { mutableStateOf(s.api.base()) }
    var username by remember { mutableStateOf(s.prefs.getString("username", "")!!) }
    var password by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var register by remember { mutableStateOf(false) }
    var allowHttp by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf("") }

    // 注册邮箱验证
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    // 忘记密码
    var forgot by remember { mutableStateOf(false) }
    var fpStep by remember { mutableIntStateOf(1) }
    var fpEmail by remember { mutableStateOf("") }
    var fpCode by remember { mutableStateOf("") }
    var fpNewPw by remember { mutableStateOf("") }
    var fpHint by remember { mutableStateOf<String?>(null) }
    var fpNotice by remember { mutableStateOf("") }
    var fpDone by remember { mutableStateOf(false) }

    var countdown by remember { mutableIntStateOf(0) }
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown -= 1
        }
    }

    val emailVerify = s.site.optBoolean("emailVerify", false)
    val minPw = s.site.optJSONObject("limits")?.optInt("passwordMinLength", 8) ?: 8

    fun submit() {
        localError = ""
        server = normalizeServer(server)
        if (register && emailVerify) {
            if (!EMAIL_RE.matches(email.trim())) {
                localError = "请填写有效邮箱"
                return
            }
            if (code.trim().length < 4) {
                localError = "请填写邮箱验证码"
                return
            }
            s.login(server, username, password, true, email, code)
        } else {
            s.login(server, username, password, register)
        }
    }

    LaunchedEffect(Unit) {
        if (s.api.base().isNotEmpty() && s.site.safeString("name").isBlank()) {
            runCatching { s.site = s.request("/api/site") }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(26.dp)
        ) {
            Spacer(Modifier.height(32.dp))
            Box(
                Modifier.size(74.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(Brush.linearGradient(listOf(Ember, Color(0xFFF29B67)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.LocalFireDepartment,
                    null,
                    Modifier.size(44.dp),
                    tint = Color.White,
                )
            }
            Spacer(Modifier.height(30.dp))
            Text(
                "你的文件，\n随身安放。",
                style =
                    MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 36.sp,
                        lineHeight = 44.sp,
                    ),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "${s.siteName} · 连接属于你的私人空间",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(34.dp))

            if (forgot) {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("找回密码", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = {
                            forgot = false
                            fpStep = 1
                            fpDone = false
                            fpNotice = ""
                            localError = ""
                        }) { Text("返回登录") }
                    }
                    Spacer(Modifier.height(18.dp))

                    if (!fpDone) {
                        OutlinedTextField(
                            username,
                            { username = it },
                            label = { Text("用户名") },
                            leadingIcon = { Icon(Icons.Outlined.PersonOutline, null) },
                            singleLine = true,
                            enabled = fpStep == 1,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (fpStep == 1) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                fpHint?.let { "验证码已发送到 $it" } ?: "验证码将发送到账号绑定的邮箱",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(18.dp))
                            Button(
                                onClick = {
                                    localError = ""
                                    s.launch {
                                        val r =
                                            s.request(
                                                "/api/auth/reset/send-code",
                                                "POST",
                                                Api.json("username", username.trim()),
                                            )
                                        fpHint = r.optString("hint", "").ifBlank { null }
                                        fpNotice = r.optString("message")
                                        fpStep = 2
                                        countdown = 60
                                    }
                                },
                                enabled = !s.busy && countdown == 0 && username.trim().length >= 3,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                            ) {
                                Text(if (countdown > 0) "重新发送（$countdown 秒）" else "发送验证码")
                            }
                        } else {
                            Spacer(Modifier.height(14.dp))
                            OutlinedTextField(
                                fpEmail,
                                { fpEmail = it },
                                label = { Text("绑定邮箱") },
                                leadingIcon = { Icon(Icons.Outlined.Mail, null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(14.dp))
                            OutlinedTextField(
                                fpCode,
                                { fpCode = it },
                                label = { Text("邮箱验证码") },
                                leadingIcon = { Icon(Icons.Outlined.Pin, null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(14.dp))
                            OutlinedTextField(
                                fpNewPw,
                                { fpNewPw = it },
                                label = { Text("新密码") },
                                leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(18.dp))
                            Button(
                                onClick = {
                                    localError = ""
                                    s.launch {
                                        val r =
                                            s.request(
                                                "/api/auth/reset",
                                                "POST",
                                                Api.json(
                                                    "username",
                                                    username.trim(),
                                                    "email",
                                                    fpEmail.trim(),
                                                    "code",
                                                    fpCode.trim(),
                                                    "newPassword",
                                                    fpNewPw,
                                                ),
                                            )
                                        fpNotice = r.optString("message", "密码已重置")
                                        fpDone = true
                                    }
                                },
                                enabled =
                                    !s.busy &&
                                        EMAIL_RE.matches(fpEmail.trim()) &&
                                        fpCode.trim().length >= 4 &&
                                        fpNewPw.length >= minPw,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                            ) { Text("重置密码") }
                        }
                        val shown = localError.ifBlank { s.failure }
                        if (shown.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                shown,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        Text(
                            fpNotice,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = {
                                forgot = false
                                fpStep = 1
                                fpDone = false
                                password = ""
                                localError = ""
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                        ) { Text("去登录") }
                    }
                }
            } else {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (register) "创建你的账户" else "欢迎回来",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        TextButton(onClick = {
                            register = !register
                            localError = ""
                        }) { Text(if (register) "去登录" else "注册") }
                    }
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        server,
                        { server = it },
                        label = { Text("服务器地址") },
                        placeholder = { Text("https://drive.example.com") },
                        leadingIcon = { Icon(Icons.Outlined.Dns, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        username,
                        { username = it },
                        label = { Text("用户名") },
                        leadingIcon = { Icon(Icons.Outlined.PersonOutline, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (register && emailVerify) {
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            email,
                            { email = it },
                            label = { Text("邮箱（必填）") },
                            supportingText = { Text("用于找回密码，不会公开展示") },
                            leadingIcon = { Icon(Icons.Outlined.Mail, null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                code,
                                { code = it },
                                label = { Text("邮箱验证码") },
                                leadingIcon = { Icon(Icons.Outlined.Pin, null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(10.dp))
                            OutlinedButton(
                                onClick = {
                                    localError = ""
                                    if (!EMAIL_RE.matches(email.trim())) {
                                        localError = "请填写有效邮箱"
                                        return@OutlinedButton
                                    }
                                    sending = true
                                    s.launch {
                                        try {
                                            val r =
                                                s.request(
                                                    "/api/auth/register/send-code",
                                                    "POST",
                                                    Api.json("email", email.trim()),
                                                )
                                            countdown = (r.optInt("expiresIn", 600) / 10).coerceIn(10, 60)
                                        } finally {
                                            sending = false
                                        }
                                    }
                                },
                                enabled = !sending && countdown == 0 && EMAIL_RE.matches(email.trim()),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.height(56.dp),
                            ) {
                                Text(
                                    if (countdown > 0) "${countdown}s"
                                    else if (sending) "发送中"
                                    else "发验证码",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        password,
                        { password = it },
                        label = { Text("密码") },
                        leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { reveal = !reveal }) {
                                Icon(
                                    if (reveal) Icons.Outlined.VisibilityOff
                                    else Icons.Outlined.Visibility,
                                    if (reveal) "隐藏密码" else "显示密码",
                                )
                            }
                        },
                        visualTransformation =
                            if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val shown = localError.ifBlank { s.failure }
                    if (shown.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            shown,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                    Button(
                        onClick = {
                            if (server.trim().startsWith("http://")) allowHttp = true else submit()
                        },
                        enabled =
                            !s.busy &&
                                server.isNotBlank() &&
                                username.isNotBlank() &&
                                password.isNotEmpty(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (s.busy) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            if (s.busy) "正在连接…" else if (register) "注册并进入" else "登录云盘",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (emailVerify) {
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = {
                                forgot = true
                                fpStep = 1
                                fpDone = false
                                fpNotice = ""
                                localError = ""
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) { Text("忘记密码？", style = MaterialTheme.typography.labelMedium) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (register && emailVerify) "注册需要邮箱验证；邮箱仅用于找回密码。"
                        else "支持 HTTPS 与可信局域网。注册规则由服务器决定。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Shield,
                    null,
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "文件保存在你的服务器，凭据由系统加密保管。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (allowHttp)
        ConfirmSheet(
            "使用 HTTP 连接？",
            "该连接不会加密账号和文件内容。可信局域网可使用，公网建议 HTTPS。",
            false,
            {
                allowHttp = false
                submit()
            },
            { allowHttp = false },
        )
}
