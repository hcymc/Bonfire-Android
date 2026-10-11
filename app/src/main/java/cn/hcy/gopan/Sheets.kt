package cn.hcy.gopan

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
internal fun ConfirmSheet(
    title: String,
    detail: String,
    danger: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (danger) Icons.Outlined.DeleteOutline else Icons.Outlined.Info, null) },
        title = { Text(title) },
        text = { Text(detail) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    "确定",
                    color =
                        if (danger) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun NameDialog(
    title: String,
    initial: String,
    busy: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value,
                { value = it },
                label = { Text("名称") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.trim()) }, enabled = value.isNotBlank() && !busy) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun FileActions(
    s: DriveState,
    f: FileChoice,
    onDismiss: () -> Unit,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FileVisual(f.item, f.directory, 52.dp)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(f.item.optString("name"), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (f.directory) "文件夹"
                        else
                            "${kind(f.item).label} · ${MainActivity.bytes(f.item.optLong("size"))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(
                Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            if (f.recycle) {
                SettingRow(Icons.Outlined.Restore, "恢复到云盘") {
                    s.mutate(
                        "/api/files/recycle/" +
                            (if (f.directory) "folder/" else "") +
                            f.item.id() +
                            "/restore",
                        "POST",
                    )
                    onDismiss()
                }
                SettingRow(Icons.Outlined.DeleteForever, "永久删除", "此操作无法撤销", onDelete)
            } else {
                if (!f.directory) {
                    SettingRow(Icons.Outlined.Visibility, "预览", onClick = onPreview)
                    SettingRow(Icons.Outlined.Download, "下载到本机", onClick = onDownload)
                    SettingRow(Icons.Outlined.Link, "创建分享链接", onClick = onShare)
                }
                SettingRow(Icons.Outlined.Edit, "重命名", onClick = onRename)
                SettingRow(Icons.Outlined.DriveFileMove, "移动到…", onClick = onMove)
                SettingRow(Icons.Outlined.DeleteOutline, "移入回收站", onClick = onDelete)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun ShareComposer(s: DriveState, file: JSONObject, onDismiss: () -> Unit) {
    val c = LocalContext.current
    val limits = s.site.optJSONObject("limits")
    var days by remember { mutableStateOf((limits?.optInt("shareDefaultDays", 7) ?: 7).toString()) }
    var password by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf("") }
    var public by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            Text(
                if (result.isBlank()) "创建分享" else "分享已准备好",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                file.optString("name"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            if (result.isBlank()) {
                OutlinedTextField(
                    days,
                    { days = it },
                    label = { Text("有效天数") },
                    supportingText = {
                        Text("留空为长期，最多 ${limits?.optInt("shareMaxDays",30)?:30} 天")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text("访问密码（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    hits,
                    { hits = it },
                    label = { Text("最大访问次数") },
                    supportingText = {
                        Text(
                            if (limits?.optBoolean("shareAllowUnlimitedHits", true) != false)
                                "留空为不限次数"
                            else "当前站点要求填写访问次数"
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(public, { public = it })
                    Column {
                        Text("在我的公开主页展示", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "关闭只隐藏主页展示，链接仍可访问。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (localError.isNotBlank())
                    Text(
                        localError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                if (s.failure.isNotBlank()) Text(s.failure, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        val d = days.toLongOrNull()
                        val h = hits.toIntOrNull()
                        localError =
                            when {
                                days.isNotBlank() &&
                                    (d == null ||
                                        d <= 0 ||
                                        d > (limits?.optInt("shareMaxDays", 30) ?: 30)) -> "请填写有效天数"
                                hits.isNotBlank() && (h == null || h <= 0) -> "次数必须为正整数"
                                password.isNotBlank() &&
                                    password.length <
                                        (limits?.optInt("shareMinPasswordLength", 4) ?: 4) ->
                                    "密码长度不符合站点要求"
                                else -> ""
                            }
                        if (localError.isBlank())
                            s.launch(true) {
                                val r =
                                    s.request(
                                        "/api/shares",
                                        "POST",
                                        Api.json(
                                            "fileId",
                                            file.id(),
                                            "expiresIn",
                                            d?.times(86400),
                                            "password",
                                            password.ifBlank { null },
                                            "maxHits",
                                            h,
                                            "visibility",
                                            if (public) "public" else "private",
                                        ),
                                    )
                                result =
                                    s.api.base() +
                                        r.optString("url") +
                                        if (password.isNotBlank()) "\n访问密码：$password" else ""
                            }
                    },
                    enabled = !s.busy,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Text(if (s.busy) "正在创建…" else "创建分享链接")
                }
            } else {
                Panel {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(result, style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(20.dp))
                Row {
                    OutlinedButton(onClick = { copy(c, s.siteName, result) }, modifier = Modifier.weight(1f)) {
                        Text("复制")
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = { send(c, result) }, modifier = Modifier.weight(1f)) {
                        Text("发送分享")
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
internal fun MoveChooser(
    s: DriveState,
    title: String,
    exclude: Set<Int>,
    onMove: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var target by remember { mutableStateOf<Int?>(null) }
    var folders by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var crumbs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    LaunchedEffect(target) {
        s.launch {
            val r = s.request("/api/files" + (target?.let { "?folderId=$it" } ?: ""))
            folders = r.rows("folders").filter { it.id() !in exclude }
            crumbs = r.rows("breadcrumb")
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding()) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { target = crumbs.dropLast(1).lastOrNull()?.id() },
                    enabled = target != null,
                ) {
                    Icon(Icons.Outlined.ArrowUpward, "上一级")
                }
                Text(
                    crumbs.lastOrNull()?.optString("name") ?: "根目录",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(folders, key = { it.id() }) { folder ->
                    SettingRow(Icons.Outlined.Folder, folder.optString("name")) {
                        target = folder.id()
                    }
                }
                if (folders.isEmpty())
                    item {
                        Text(
                            "这里没有子文件夹",
                            Modifier.padding(20.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onMove(target) },
                enabled = !s.busy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(if (s.busy) "正在移动…" else "移动到此处")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 从链接或裸文本里提取 32 位分享码。 */
internal fun parseShareToken(raw: String): String? =
    Regex("[0-9a-fA-F]{32}").find(raw.trim())?.value?.lowercase()

/** 一次性把分享文件取回应用私有下载目录，返回实际保存的文件名（ContentProvider 会剥掉数字前缀）。 */
private fun fetchShareFile(
    context: android.content.Context,
    api: Api,
    token: String,
    ticket: String,
    rawName: String,
    onProgress: (Float) -> Unit,
): String {
    val name = rawName.replace(Regex("[\\\\/\\p{Cntrl}]"), "_").ifBlank { "share-file" }
    val dir = File(context.filesDir, "downloads")
    if (!dir.exists() && !dir.mkdirs()) throw IOException("无法创建下载目录")
    var dest = File(dir, "${System.currentTimeMillis()}-$name")
    var n = 1
    while (dest.exists()) dest = File(dir, "${System.currentTimeMillis()}-${n++}-$name")
    val conn = api.open("/api/public/shares/$token/file?ticket=$ticket", "GET")
    try {
        val code = conn.responseCode
        if (code < 200 || code >= 300) {
            val body =
                conn.errorStream?.let { String(Api.readLimited(it, 1 shl 20), Charsets.UTF_8) }
            throw IOException(
                Api.parse(body).safeString("error").ifBlank { "下载失败（服务器返回 $code）" }
            )
        }
        val total = conn.contentLengthLong
        java.io.FileOutputStream(dest).use { out ->
            conn.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                var read = 0L
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    out.write(buf, 0, r)
                    read += r
                    if (total > 0) onProgress((read.toDouble() / total).toFloat().coerceIn(0f, 1f))
                }
            }
            out.fd.sync()
        }
        if (total > 0 && dest.length() != total) throw IOException("下载不完整，请重试")
    } finally {
        conn.disconnect()
    }
    return dest.name
}

/** 查看别人的分享：粘贴链接读元数据，密码分享先解锁，再凭一次性 ticket 取回文件。 */
@Composable
internal fun ShareViewer(s: DriveState, onDismiss: () -> Unit) {
    val c = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var link by remember { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    var meta by remember { mutableStateOf<JSONObject?>(null) }
    var password by remember { mutableStateOf("") }
    var ticket by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var savedName by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf("") }

    fun load() {
        val t = parseShareToken(link)
        if (t == null) {
            localError = "没有找到 32 位分享码，请粘贴完整分享链接"
            return
        }
        // 统一进独立的分享详情页（预览 + 下载入传输中心）。
        onDismiss()
        s.openShare(t)
    }

    fun download() {
        val t = token ?: return
        val m = meta ?: return
        if (progress != null) return
        s.launch {
            try {
                val tk =
                    withContext(Dispatchers.IO) {
                        s.request(
                                "/api/public/shares/$t/unlock",
                                "POST",
                                Api.json("password", password.ifBlank { null }),
                            )
                            .getString("ticket")
                    }
                ticket = tk
                savedName = null
                progress = 0f
                val name =
                    withContext(Dispatchers.IO) {
                        fetchShareFile(s.context, s.api, t, tk, m.optString("name")) {
                            progress = it
                        }
                    }
                progress = null
                savedName = name
                s.notice = "分享文件已保存到本机"
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                ticket = null
                progress = null
                s.failure = e.message ?: "下载中断，请重试"
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            Text("查看分享", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "粘贴别人发的分享链接，查看内容后一键取回。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                link,
                { link = it },
                label = { Text("分享链接") },
                placeholder = { Text("https://…/s/…") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        val text = clipboard.getText()?.text.orEmpty().trim()
                        if (text.isNotEmpty()) {
                            link = text
                            load()
                        }
                    }) {
                        Icon(Icons.Outlined.ContentPaste, "粘贴并查看")
                    }
                },
            )
            if (localError.isNotBlank() || s.failure.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    if (localError.isNotBlank()) localError else s.failure,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { load() },
                enabled = link.isNotBlank() && !s.busy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(if (s.busy && meta == null) "正在查看…" else "查看分享")
            }
            meta?.let { m ->
                val available = m.optBoolean("available")
                val needsPassword = m.optBoolean("needsPassword")
                val unlocked = ticket != null || !needsPassword
                Spacer(Modifier.height(22.dp))
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val k = kind(m, false)
                        Box(
                            Modifier.size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(k.tint.copy(alpha = .1f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(k.icon, null, Modifier.size(24.dp), tint = k.tint)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                m.optString("name"),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "来自 @${m.optString("owner")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    val expires = m.optLong("expiresAt")
                    Text(
                        MainActivity.bytes(m.optLong("size")) +
                            " · 已下载 ${m.optInt("hits")} 次" +
                            (if (expires > 0)
                                " · 到期 " +
                                    android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", expires)
                            else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when {
                            !available -> m.safeString("reason").ifBlank { "链接不可用" }
                            needsPassword && !unlocked -> "需要访问密码"
                            else -> "可以下载"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            when {
                                !available -> MaterialTheme.colorScheme.error
                                unlocked -> Color(0xFF699879)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
                if (available && needsPassword && ticket == null) {
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        password,
                        { password = it },
                        label = { Text("分享密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (available) {
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { download() },
                        enabled =
                            unlocked && progress == null ||
                                (needsPassword && password.isNotBlank() && progress == null),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Text(
                            when {
                                progress != null -> "正在下载…"
                                savedName != null -> "重新下载"
                                needsPassword && !unlocked ->
                                    if (password.isBlank()) "先填写访问密码" else "解锁并下载"
                                else -> "下载到本机"
                            }
                        )
                    }
                    progress?.let { p ->
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { p },
                            modifier =
                                Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        )
                    }
                    savedName?.let { name ->
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                        val uri =
                                            Uri.parse(
                                                "content://cn.hcy.gopan.files/" + Uri.encode(name)
                                            )
                                        c.startActivity(
                                            Intent(Intent.ACTION_VIEW)
                                                .setDataAndType(uri, c.contentResolver.getType(uri))
                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        )
                                    }
                                    .onFailure {
                                        s.failure = "无法打开此文件，可能没有支持的应用。"
                                    }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("打开文件")
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
internal fun EditProfile(s: DriveState, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(s.user?.safeString("display_name").orEmpty()) }
    var bio by remember { mutableStateOf(s.user?.safeString("bio").orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).imePadding().navigationBarsPadding()) {
            Text("编辑资料", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Avatar(s.user, 72.dp)
            }
            Spacer(Modifier.height(22.dp))
            OutlinedTextField(
                name,
                { if (it.length <= 24) name = it },
                label = { Text("昵称") },
                supportingText = { Text("${name.length}/24") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                bio,
                { if (it.length <= 200) bio = it },
                label = { Text("简介") },
                supportingText = { Text("${bio.length}/200") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
            if (s.failure.isNotBlank()) Text(s.failure, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    s.launch(true) {
                        s.user =
                            s.request("/api/me", "PATCH", Api.json("displayName", name, "bio", bio))
                                .getJSONObject("profile")
                        s.notice = "资料已更新"
                        onDismiss()
                    }
                },
                enabled = !s.busy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(if (s.busy) "正在保存…" else "保存资料")
            }
            TextButton(
                onClick = {
                    s.launch {
                        s.user = s.request("/api/me/avatar", "DELETE").getJSONObject("profile")
                        s.notice = "头像已移除"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("移除当前头像")
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
internal fun PasswordDialog(s: DriveState, onDismiss: () -> Unit) {
    var old by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).imePadding().navigationBarsPadding()) {
            Text("修改密码", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(10.dp))
            Text(
                "修改成功后，其他设备的登录会话将退出。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            listOf(
                    Triple(old, "当前密码", { v: String -> old = v }),
                    Triple(next, "新密码", { v: String -> next = v }),
                    Triple(repeat, "再次输入新密码", { v: String -> repeat = v }),
                )
                .forEach { (value, label, update) ->
                    OutlinedTextField(
                        value,
                        update,
                        label = { Text(label) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                }
            if (repeat.isNotBlank() && repeat != next)
                Text("两次密码不一致", color = MaterialTheme.colorScheme.error)
            if (s.failure.isNotBlank()) Text(s.failure, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    s.launch(true) {
                        s.request(
                            "/api/me/password",
                            "POST",
                            Api.json("oldPassword", old, "newPassword", next),
                        )
                        s.notice = "密码已更新"
                        onDismiss()
                    }
                },
                enabled =
                    !s.busy &&
                        old.isNotBlank() &&
                        next.length >=
                            (s.site.optJSONObject("limits")?.optInt("passwordMinLength", 8) ?: 8) &&
                        repeat == next,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text("更新密码")
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
internal fun SessionSheet(s: DriveState, rows: List<JSONObject>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding()) {
            Text("登录设备", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(18.dp))
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(rows, key = { it.optString("id") }) { r ->
                    ListItem(
                        headlineContent = {
                            Text(r.optString("device").ifBlank { "未知设备" })
                        },
                        supportingContent = {
                            Text(
                                (if (r.optBoolean("current")) "当前会话 · " else "") +
                                    "登录于 " + MainActivity.time(r.optString("created_at"))
                            )
                        },
                        leadingContent = { Icon(Icons.Outlined.Devices, null) },
                        trailingContent = {
                            if (r.optBoolean("current"))
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                        },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            OutlinedButton(
                onClick = {
                    s.launch {
                        val r = s.request("/api/me/sessions", "DELETE")
                        s.notice = "已退出 ${r.optInt("revoked")} 个其他会话"
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("退出其他所有设备")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun MessageComposer(s: DriveState, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).imePadding().navigationBarsPadding()) {
            Text("写一条留言", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(s.user, 38.dp)
                Spacer(Modifier.width(12.dp))
                Text(s.user?.name().orEmpty(), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                text,
                { if (it.length <= 2000) text = it },
                placeholder = { Text("记录今天的小事，或分享一个新发现…") },
                minLines = 5,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                supportingText = { Text("${text.length}/2000") },
            )
            if (s.failure.isNotBlank()) Text(s.failure, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    s.launch(true) {
                        s.request("/api/guestbook", "POST", Api.json("message", text))
                        s.messages = s.request("/api/guestbook?limit=100").rows("messages")
                        onDismiss()
                        s.notice = "留言已发布"
                    }
                },
                enabled = !s.busy && text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(if (s.busy) "正在发布…" else "发布留言")
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
internal fun UserSheet(s: DriveState, data: JSONObject, onDismiss: () -> Unit) {
    val c = LocalContext.current
    val profile = data.optJSONObject("profile")
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(profile, 72.dp)
                Column(Modifier.padding(start = 18.dp)) {
                    Text(profile?.name().orEmpty(), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "@" + profile?.optString("username"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(profile?.safeString("bio").orEmpty(), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            SectionTitle("公开分享")
            LazyColumn(Modifier.heightIn(max = 350.dp)) {
                items(data.rows("shares"), key = { it.id() }) { sh ->
                    SettingRow(
                        Icons.Outlined.Link,
                        sh.optString("file_name", sh.optString("name")),
                    ) {
                        // 直接打开分享详情页（预览 + 下载入传输中心）。
                        onDismiss()
                        s.openShare(sh.optString("token"))
                    }
                }
                if (data.rows("shares").isEmpty())
                    item {
                        Text(
                            "还没有公开分享",
                            Modifier.padding(vertical = 22.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
