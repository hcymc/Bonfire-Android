package cn.hcy.gopan

import android.content.*
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
internal fun PageHeader(
    title: String,
    subtitle: String = "",
    back: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back != null)
            IconButton(onClick = back, modifier = Modifier.padding(end = 6.dp)) {
                Icon(Icons.Outlined.ArrowBack, "返回")
            }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        actions()
    }
}

@Composable
internal fun TransfersPage(
    s: DriveState,
    onCancel: (JSONObject) -> Unit,
    onExport: (JSONObject) -> Unit,
) {
    val c = LocalContext.current
    var segment by remember { mutableIntStateOf(0) }
    val active = s.tasks.filter { it.optString("state") !in listOf("done", "cancelled") }
    val finished = s.tasks.filter { it.optString("state") in listOf("done", "cancelled") }
    val entries = if (segment == 0) active else finished
    // 前台兜底：调度器可能无限期压着任务（运行配额/厂商省电/退避），页面开着时
    // 周期性把排队和卡住的任务直接接管到进程内执行，不再干等系统。
    LaunchedEffect(Unit) {
        while (true) {
            for (t in s.tasks) {
                val st = t.optString("state")
                if (st == "queued" || st == "running") Transfers.runNow(c, t.id())
            }
            kotlinx.coroutines.delay(10_000)
        }
    }
    Column {
        PageHeader("传输中心", "上传和下载，都有迹可循") {
            IconButton(
                onClick = {
                    s.launch {
                        withContext(Dispatchers.IO) { Transfers.pauseOwner(c) }
                        s.refreshTasks()
                    }
                }
            ) {
                Icon(Icons.Outlined.PauseCircleOutline, "全部暂停")
            }
            IconButton(
                onClick = {
                    s.launch {
                        withContext(Dispatchers.IO) {
                            TaskStore.get(c).clearFinished(s.api.owner())
                        }
                        s.refreshTasks()
                    }
                }
            ) {
                Icon(Icons.Outlined.DeleteSweep, "清理完成记录")
            }
        }
        Row(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilterChip(
                selected = segment == 0,
                onClick = { segment = 0 },
                label = { Text("进行中  ${active.size}") },
            )
            FilterChip(
                selected = segment == 1,
                onClick = { segment = 1 },
                label = { Text("已完成  ${finished.size}") },
            )
        }
        Spacer(Modifier.height(12.dp))
        if (entries.isEmpty())
            Empty(
                Icons.Outlined.SwapVert,
                if (segment == 0) "暂时没有传输任务" else "还没有完成记录",
                "从云盘上传或下载，任务会在这里持续更新。",
            )
        else
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(entries, key = { it.id() }) { t -> TransferCard(s, t, onCancel, onExport) }
            }
    }
}

@Composable
private fun TransferCard(
    s: DriveState,
    t: JSONObject,
    onCancel: (JSONObject) -> Unit,
    onExport: (JSONObject) -> Unit,
) {
    val c = LocalContext.current
    val state = t.optString("state")
    val done = t.optLong("done")
    val size = t.optLong("size")
    val fraction = (done.toFloat() / size.coerceAtLeast(1)).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(fraction, tween(450), label = "transferProgress")
    val uploading = t.optString("kind") == "upload"
    val label =
        when (state) {
            "running" -> "正在" + (if (uploading) "上传" else "下载")
            "queued" -> "等待网络 / 系统调度"
            "paused" -> "已暂停"
            "failed" -> "传输失败"
            "done" -> "已完成"
            else -> "已取消"
        }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (uploading) Icons.Outlined.CloudUpload else Icons.Outlined.CloudDownload,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    t.optString("name"),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (state == "failed") MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state == "done") Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF699879))
        }
        Spacer(Modifier.height(18.dp))
        if (state !in listOf("done", "cancelled"))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer,
            )
        Spacer(Modifier.height(10.dp))
        Row {
            Text(
                "${MainActivity.bytes(done)} / ${MainActivity.bytes(size)}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${(fraction*100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (t.safeString("error").isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                t.optString("error"),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state == "running" && t.optLong("speed") > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${MainActivity.bytes(t.optLong("speed"))}/s · 剩余 ${TransferRate.time(t.optLong("eta", -1))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (state in listOf("running", "queued"))
                TextButton(onClick = { s.pause(t) }) {
                    Icon(Icons.Outlined.Pause, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("暂停")
                }
            else if (state in listOf("paused", "failed"))
                FilledTonalButton(onClick = { s.resume(t) }) {
                    Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp))
                    Text("继续")
                }
            if (state !in listOf("done", "cancelled"))
                TextButton(onClick = { onCancel(t) }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            if (state == "done" && t.safeString("local").isNotBlank()) {
                TextButton(onClick = { onExport(t) }) { Text("另存为") }
                FilledTonalButton(
                    onClick = {
                        runCatching {
                                val f = File(t.optString("local"))
                                check(f.isFile()) { "本地文件已不存在" }
                                val uri =
                                    Uri.parse("content://cn.hcy.gopan.files/" + Uri.encode(f.name))
                                c.startActivity(
                                    Intent(Intent.ACTION_VIEW)
                                        .setDataAndType(uri, c.contentResolver.getType(uri))
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                )
                            }
                            .onFailure { s.failure = "无法打开此文件，可先另存后用对应应用打开。" }
                    }
                ) {
                    Text("打开文件")
                }
            }
        }
    }
}

@Composable
internal fun ProfilePage(
    s: DriveState,
    onEdit: () -> Unit,
    onSecurity: () -> Unit,
    onSessions: () -> Unit,
    onLogout: () -> Unit,
    onShares: () -> Unit,
) {
    val person = s.user ?: return
    val colors = MaterialTheme.colorScheme
    val avatarPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            it?.let { s.uploadAvatar(it) }
        }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageHeader("我的空间", "每一次登录，都回到熟悉的地方") {
                IconButton(onClick = { s.page = "settings" }) {
                    Icon(Icons.Outlined.Settings, "设置")
                }
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Avatar(person, 80.dp) { avatarPicker.launch(arrayOf("image/*")) }
                        Box(
                            Modifier.align(Alignment.BottomEnd)
                                .size(25.dp)
                                .background(colors.primary, CircleShape)
                                .border(2.dp, colors.surface, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.PhotoCamera,
                                "更换头像",
                                Modifier.size(13.dp),
                                tint = colors.onPrimary,
                            )
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 18.dp)) {
                        Text(person.name(), style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "@${person.optString("username")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(9.dp))
                        Surface(color = colors.primaryContainer, shape = RoundedCornerShape(7.dp)) {
                            Text(
                                if (person.optString("role") == "admin") "管理员" else "云盘成员",
                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    person.safeString("bio").ifBlank { "收藏日常，也收藏灵感。" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("编辑资料")
                    }
                    Spacer(Modifier.width(12.dp))
                    FilledTonalButton(
                        onClick = { avatarPicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f),
                        enabled = !s.busy,
                    ) {
                        Text(if (s.busy) "正在处理…" else "更换头像")
                    }
                }
            }
        }
        item {
            Panel {
                SectionTitle("存储空间")
                Spacer(Modifier.height(16.dp))
                val used = person.optLong("used_bytes")
                val total = person.optLong("quota_bytes").coerceAtLeast(1)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        MainActivity.bytes(used),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.primary,
                    )
                    Text(
                        " / ${MainActivity.bytes(total)}",
                        Modifier.padding(bottom = 3.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(15.dp))
                LinearProgressIndicator(
                    progress = { (used.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                    trackColor = colors.primaryContainer,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "剩余 ${MainActivity.bytes((total-used).coerceAtLeast(0))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = colors.surface) {
                Column {
                    SettingRow(Icons.Outlined.RestoreFromTrash, "回收站", "恢复文件与文件夹") {
                        s.page = "recycle"
                        s.refresh()
                    }
                    SettingRow(Icons.Outlined.Lock, "修改密码", "密码修改后其他设备会退出", onSecurity)
                    SettingRow(Icons.Outlined.Devices, "登录设备", "查看会话并退出其他设备", onSessions)
                }
            }
        }
        item {
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
            ) {
                Icon(Icons.Outlined.Logout, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("退出当前账户")
            }
        }
    }
}

@Composable
internal fun CommunityPage(
    s: DriveState,
    onConfirm: (ConfirmAction) -> Unit,
    onUser: (String) -> Unit,
) {
    var composer by remember { mutableStateOf(false) }
    if (composer) MessageComposer(s) { composer = false }
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageHeader("留言板", "写下想法，看看大家的近况") {
                IconButton(onClick = { s.refresh() }) { Icon(Icons.Outlined.Refresh, "刷新") }
            }
        }
        if (s.site.safeString("announcement").isNotBlank())
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Campaign,
                                null,
                                Modifier.size(21.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "站点公告",
                                Modifier.padding(start = 9.dp),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            s.site.optString("announcement"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(s.user, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "今天有什么想分享的？",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = { composer = true },
                        enabled = s.site.optBoolean("guestbookOpen", true),
                    ) {
                        Text("写留言")
                    }
                }
            }
        }
        if (s.messages.isEmpty() && !s.loading)
            item { Empty(Icons.Outlined.Forum, "还没有留言", "写下第一条留言，和大家打个招呼。") }
        items(s.messages, key = { it.id() }) { m ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(m, 40.dp) {
                        if (m.safeString("username").isNotBlank()) onUser(m.optString("username"))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            m.name().ifBlank { m.optString("author") },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            MainActivity.time(m.optString("created_at")),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (
                        m.optLong("user_id") == s.user?.optLong("id") ||
                            s.user?.optString("role") == "admin"
                    )
                        IconButton(
                            onClick = {
                                onConfirm(
                                    ConfirmAction("删除留言？", "删除后无法恢复。", true) {
                                        s.mutate("/api/guestbook/${m.id()}", "DELETE")
                                    }
                                )
                            }
                        ) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                "删除留言",
                                Modifier.size(19.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                }
                Spacer(Modifier.height(16.dp))
                Text(m.optString("message"), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
internal fun RecyclePage(s: DriveState, onMenu: (JSONObject, Boolean) -> Unit) {
    Column {
        PageHeader(
            "回收站",
            "误删的文件，也能再找回来",
            {
                s.page = "main"
                s.refresh()
            },
        )
        if (s.recycled.isEmpty() && !s.loading)
            Empty(Icons.Outlined.RestoreFromTrash, "回收站很干净", "删除的文件会保留在这里，可恢复或彻底删除。")
        else
            LazyColumn(contentPadding = PaddingValues(20.dp)) {
                items(s.recycled, key = { (if (it.optBoolean("_dir")) "d" else "f") + it.id() }) { f
                    ->
                    FileRow(
                        FileChoice(f, f.optBoolean("_dir"), true),
                        onClick = { onMenu(f, f.optBoolean("_dir")) },
                        onMenu = { onMenu(f, f.optBoolean("_dir")) },
                    )
                }
            }
    }
}

@Composable
internal fun SharesPage(s: DriveState, onConfirm: (ConfirmAction) -> Unit) {
    val c = LocalContext.current
    var viewer by remember { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageHeader(
                "我的分享",
                "链接权限与主页展示分别管理",
                if (s.page != "main")
                    ({
                        s.page = "main"
                        s.refresh()
                    })
                else null,
            ) {
                IconButton(onClick = { viewer = true }) {
                    Icon(Icons.Outlined.Visibility, "查看分享")
                }
            }
        }
        if (s.shares.isEmpty())
            item { Empty(Icons.Outlined.Link, "还没有分享链接", "在文件的更多操作里，创建你的第一份分享。") }
        items(s.shares, key = { it.id() }) { sh ->
            var menu by remember { mutableStateOf(false) }
            val expiry = sh.optLong("expires_at")
            val hits = sh.optInt("max_hits")
            val expired = expiry > 0 && expiry < System.currentTimeMillis()
            val exhausted = hits > 0 && sh.optInt("hits") >= hits
            val url = s.api.base() + "/s/" + sh.optString("token")
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(42.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Link, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            sh.optString("file_name"),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (expired) "已过期" else if (exhausted) "次数已用完" else "链接有效",
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                if (expired || exhausted) MaterialTheme.colorScheme.error
                                else Color(0xFF699879),
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Outlined.MoreVert, "管理分享")
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (sh.optString("visibility") == "public") "从公开主页隐藏"
                                        else "在公开主页展示"
                                    )
                                },
                                onClick = {
                                    menu = false
                                    s.mutate(
                                        "/api/shares/${sh.id()}",
                                        "PATCH",
                                        Api.json(
                                            "visibility",
                                            if (sh.optString("visibility") == "public") "private"
                                            else "public",
                                        ),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("撤销分享", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menu = false
                                    onConfirm(
                                        ConfirmAction("撤销分享？", "已有链接将无法继续访问。", true) {
                                            s.mutate("/api/shares/${sh.id()}", "DELETE")
                                        }
                                    )
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LabelIcon(
                        if (sh.optBoolean("needs_password")) Icons.Outlined.Lock
                        else Icons.Outlined.LockOpen,
                        if (sh.optBoolean("needs_password")) "密码保护" else "无需密码",
                    )
                    LabelIcon(
                        Icons.Outlined.Visibility,
                        "${sh.optInt("hits")} / ${if(hits==0)"不限"else hits} 次",
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (expiry > 0)
                        "到期：" + android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", expiry)
                    else "长期有效",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Row {
                    OutlinedButton(onClick = { copy(c, s.siteName, url) }, modifier = Modifier.weight(1f)) {
                        Text("复制链接")
                    }
                    Spacer(Modifier.width(10.dp))
                    FilledTonalButton(onClick = { send(c, url) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.IosShare, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("分享")
                    }
                }
            }
        }
    }
    if (viewer) ShareViewer(s) { viewer = false }
}

@Composable
private fun LabelIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(5.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun copy(c: android.content.Context, label: String, text: String) {
    (c.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText(label, text))
    android.widget.Toast.makeText(c, "已复制", android.widget.Toast.LENGTH_SHORT).show()
}

internal fun send(c: android.content.Context, text: String) {
    c.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
            "分享云盘链接",
        )
    )
}
