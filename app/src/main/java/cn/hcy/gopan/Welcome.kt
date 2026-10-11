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

/** 补全用户输入的服务器地址：缺协议时默认走 HTTPS，已是完整 URL 则原样保留。 */
internal fun normalizeServer(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    val lowered = trimmed.lowercase()
    return if (lowered.startsWith("http://") || lowered.startsWith("https://")) trimmed
    else "https://$trimmed"
}

@Composable
internal fun Welcome(s: DriveState) {
    var server by remember { mutableStateOf(s.api.base()) }
    var username by remember { mutableStateOf(s.prefs.getString("username", "")!!) }
    var password by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var register by remember { mutableStateOf(false) }
    var allowHttp by remember { mutableStateOf(false) }
    fun submit() {
        server = normalizeServer(server)
        s.login(server, username, password, register)
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
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (register) "创建你的账户" else "欢迎回来",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    TextButton(onClick = { register = !register }) {
                        Text(if (register) "去登录" else "注册")
                    }
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
                if (s.failure.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        s.failure,
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
                Spacer(Modifier.height(14.dp))
                Text(
                    "支持 HTTPS 与可信局域网。注册规则由服务器决定。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
