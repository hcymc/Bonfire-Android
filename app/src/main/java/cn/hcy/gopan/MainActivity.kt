package cn.hcy.gopan

import android.Manifest
import android.content.*
import android.net.Uri
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import java.util.Locale
import kotlinx.coroutines.*
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 前台计数：>0 表示用户正看着 App。Transfers 用它在调度器压着任务不放时
        // 直接进程内执行（见 Transfers.runNow）。
        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                Transfers.foreground.incrementAndGet()
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                Transfers.foreground.decrementAndGet()
            }

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
        consume(intent)
        setContent {
            val scope = rememberCoroutineScope()
            val state = remember { DriveState(applicationContext, scope) }
            LaunchedEffect(Unit) {
                state.tab = intent.getIntExtra("tab", 0)
                state.boot()
            }
            DriveTheme(state) { DriveApp(state, incoming) { incoming = emptyList() } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consume(intent)
    }

    private fun consume(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND)
            incoming = listOfNotNull(i.getParcelableExtra(Intent.EXTRA_STREAM))
        else if (i?.action == Intent.ACTION_SEND_MULTIPLE)
            incoming = i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
    }

    companion object {
        @JvmStatic
        fun bytes(value: Long): String =
            when {
                value < 1024 -> "$value B"
                value < 1048576 -> String.format(Locale.CHINA, "%.1f KB", value / 1024.0)
                value < 1073741824 -> String.format(Locale.CHINA, "%.1f MB", value / 1048576.0)
                else -> String.format(Locale.CHINA, "%.2f GB", value / 1073741824.0)
            }

        // 服务端时间契约（服务端 README「API 时间约定」）：字符串 *_at 一律是服务器换算好的
        // 北京时间（无时区标记），按墙钟原样展示；数字是 epoch 毫秒，按设备时区格式化；
        // 带 Z/偏移的 ISO 串按 UTC 解析转本地，为日后大版本迁移预留兼容。
        @JvmStatic
        fun time(raw: String?): String {
            val s = (raw ?: "").trim()
            if (s.isEmpty()) return ""
            if (s.matches(Regex("\\d+"))) return time(s.toLong())
            val offsetLike = Regex("[+-]\\d{2}:?\\d{2}$")
            if (s.contains('T') && (s.endsWith("Z") || offsetLike.containsMatchIn(s))) {
                return try {
                    val p = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                    p.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    val d = p.parse(s.take(19))
                    if (d == null) s.replace('T', ' ').take(16) else time(d.time)
                } catch (e: Exception) {
                    s.replace('T', ' ').take(16)
                }
            }
            return s.replace('T', ' ').take(16)
        }

        @JvmStatic
        fun time(ms: Long): String =
            if (ms <= 0) ""
            else android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", ms).toString()
    }
}

@Composable
internal fun DriveApp(s: DriveState, incoming: List<Uri>, clearIncoming: () -> Unit) {
    val c = androidx.compose.ui.platform.LocalContext.current
    val snack = remember { SnackbarHostState() }
    UpdateGate(s)
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val permissionBatch =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    fun notifyPermission() {
        if (Build.VERSION.SDK_INT >= 33) permissions.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    // 登录后主动申请全部运行时权限，避免等到首次传输/提醒才被系统拦截。
    LaunchedEffect(s.user) {
        if (s.user != null) {
            val wanted = buildList {
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                if (Build.VERSION.SDK_INT >= 36) add("android.permission.POST_PROMOTED_NOTIFICATIONS")
            }
            if (wanted.isNotEmpty()) permissionBatch.launch(wanted.toTypedArray())
        }
    }
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                notifyPermission()
                s.upload(uris, true)
            }
        }
    var confirm by remember { mutableStateOf<ConfirmAction?>(null) }
    var fileMenu by remember { mutableStateOf<FileChoice?>(null) }
    var rename by remember { mutableStateOf<FileChoice?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var shareFile by remember { mutableStateOf<JSONObject?>(null) }
    var moveFile by remember { mutableStateOf<FileChoice?>(null) }
    var batchMoveOpen by remember { mutableStateOf(false) }
    var profileEdit by remember { mutableStateOf(false) }
    var passwordEdit by remember { mutableStateOf(false) }
    var emailEdit by remember { mutableStateOf(false) }
    var emailNudgeDismissed by remember { mutableStateOf(s.prefs.getBoolean("email_nudge_hide", false)) }
    var sessions by remember { mutableStateOf<List<JSONObject>?>(null) }
    var publicUser by remember { mutableStateOf<JSONObject?>(null) }
    var exportTask by remember { mutableStateOf<JSONObject?>(null) }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            val t = exportTask
            if (uri != null && t != null)
                s.launch {
                    withContext(Dispatchers.IO) {
                        java.io.FileInputStream(t.optString("local")).use { input ->
                            c.contentResolver.openOutputStream(uri, "wt")!!.use { input.copyTo(it) }
                        }
                    }
                    s.notice = "文件已另存"
                }
        }
    fun download(file: JSONObject) {
        notifyPermission()
        s.download(file)
    }
    fun preview(file: JSONObject) {
        c.startActivity(Intent(c, PreviewActivity::class.java).putExtra("file", file.toString()))
    }
    LaunchedEffect(s.notice) {
        if (s.notice.isNotEmpty()) {
            snack.showSnackbar(s.notice)
            s.notice = ""
        }
    }
    LaunchedEffect(s.user, s.tab) {
        while (s.user != null && s.tab == 1) {
            s.refreshTasks()
            delay(1000)
        }
    }
    BackHandler(
        enabled =
            s.user != null &&
                (s.page != "main" || s.folder != null || s.tab != 0 || s.selected.isNotEmpty())
    ) {
        s.back()
    }
    if (s.booting) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }
    if (s.user == null) {
        Welcome(s)
        return
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = { if (s.page == "main") DriveNavigation(s.tab) { s.navigate(it) } },
        floatingActionButton = {
            if (s.page == "main" && s.tab == 0 && s.selected.isEmpty())
                ExtendedFloatingActionButton(
                    onClick = { pick.launch(arrayOf("*/*")) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text("上传文件") },
                )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 老账号未绑邮箱：提示一次，可关闭；点「去绑定」进账户页。
            if (
                s.site.optBoolean("emailVerify", false) &&
                    s.email == null &&
                    !emailNudgeDismissed &&
                    s.page == "main"
            )
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Mail, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "你还没有绑定邮箱——绑定后忘记密码可自助找回。",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = {
                            emailNudgeDismissed = true
                            s.prefs.edit().putBoolean("email_nudge_hide", true).apply()
                            s.tab = 4
                            s.page = "main"
                            emailEdit = true
                        }) { Text("去绑定") }
                        IconButton(onClick = {
                            emailNudgeDismissed = true
                            s.prefs.edit().putBoolean("email_nudge_hide", true).apply()
                        }) { Icon(Icons.Outlined.Close, "关闭提醒", Modifier.size(15.dp)) }
                    }
                }
            if (s.failure.isNotEmpty())
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            s.failure,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = { s.refresh() }) { Text("重试") }
                        IconButton(onClick = { s.failure = "" }) {
                            Icon(Icons.Outlined.Close, null)
                        }
                    }
                }
            AnimatedContent(
                targetState = s.page to s.tab,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 24 }) togetherWith
                        fadeOut(tween(100))
                },
                label = "navigation",
            ) { destination ->
                when (destination.first) {
                    "recycle" ->
                        RecyclePage(s) { item, dir -> fileMenu = FileChoice(item, dir, true) }
                    "settings" -> SettingsPage(s) { confirm = it }
                    "shares" -> SharesPage(s) { confirm = it }
                    "share" -> SharePage(s)
                    else ->
                        when (destination.second) {
                            0 ->
                                FilesPage(
                                    s,
                                    onUpload = { pick.launch(arrayOf("*/*")) },
                                    onNewFolder = { newFolder = true },
                                    onFile = { item, dir ->
                                        if (s.selected.isNotEmpty()) s.select(item, dir)
                                        else if (dir) s.enter(item.id()) else preview(item)
                                    },
                                    onMenu = { item, dir -> fileMenu = FileChoice(item, dir) },
                                    onBatchMove = { batchMoveOpen = true },
                                    onBatchDelete = {
                                        confirm =
                                            ConfirmAction("移入回收站", "所选文件和文件夹可在回收站恢复。", true) {
                                                s.batch(true)
                                            }
                                    },
                                )
                            1 ->
                                TransfersPage(
                                    s,
                                    onCancel = { t ->
                                        confirm =
                                            ConfirmAction("取消传输？", "断点和服务器预留空间会被释放。", true) {
                                                s.cancel(t)
                                            }
                                    },
                                    onExport = {
                                        exportTask = it
                                        export.launch(it.optString("name"))
                                    },
                                )
                            2 -> SharesPage(s) { confirm = it }
                            3 ->
                                CommunityPage(
                                    s,
                                    onConfirm = { confirm = it },
                                    onUser = { username ->
                                        s.launch {
                                            publicUser =
                                                s.request("/api/users/${Uri.encode(username)}")
                                        }
                                    },
                                )
                            4 ->
                                ProfilePage(
                                    s,
                                    onEdit = { profileEdit = true },
                                    onSecurity = { passwordEdit = true },
                                    onSessions = {
                                        s.launch {
                                            sessions =
                                                s.request("/api/me/sessions").rows("sessions")
                                        }
                                    },
                                    onLogout = {
                                        confirm =
                                            ConfirmAction("退出当前账户？", "传输任务会暂停并保留断点。", false) {
                                                s.logout()
                                            }
                                    },
                                    onShares = {
                                        s.page = "shares"
                                        s.launch {
                                            s.shares = s.request("/api/shares").rows("shares")
                                        }
                                    },
                                    onEmail = { emailEdit = true },
                                )
                        }
                }
            }
        }
    }
    if (incoming.isNotEmpty())
        ConfirmSheet(
            "上传分享来的文件",
            "将 ${incoming.size} 个文件上传到当前目录。",
            false,
            {
                s.upload(incoming, false)
                clearIncoming()
            },
            clearIncoming,
        )
    confirm?.let {
        ConfirmSheet(
            it.title,
            it.detail,
            it.danger,
            {
                it.action()
                confirm = null
            },
            { confirm = null },
        )
    }
    fileMenu?.let { choice ->
        FileActions(
            s,
            choice,
            onDismiss = { fileMenu = null },
            onPreview = {
                preview(choice.item)
                fileMenu = null
            },
            onDownload = {
                download(choice.item)
                fileMenu = null
            },
            onRename = {
                rename = choice
                fileMenu = null
            },
            onShare = {
                shareFile = choice.item
                fileMenu = null
            },
            onMove = {
                moveFile = choice
                fileMenu = null
            },
            onDelete = {
                fileMenu = null
                val path = if (choice.recycle) "/api/files/recycle/" else "/api/files/"
                confirm =
                    ConfirmAction(
                        if (choice.recycle) "永久删除？" else "移入回收站？",
                        if (choice.recycle) "此操作无法撤销。" else "文件夹及内容将一起移入回收站。",
                        true,
                    ) {
                        s.mutate(
                            path + (if (choice.directory) "folder/" else "") + choice.item.id(),
                            "DELETE",
                        )
                    }
            },
        )
    }
    rename?.let { f ->
        NameDialog(
            "重命名",
            f.item.optString("name"),
            s.busy,
            { name ->
                s.mutate(
                    "/api/files/" + (if (f.directory) "folder/" else "") + f.item.id(),
                    "PATCH",
                    Api.json("name", name),
                )
                rename = null
            },
            { rename = null },
        )
    }
    if (newFolder)
        NameDialog(
            "新建文件夹",
            "",
            s.busy,
            { name ->
                s.mutate("/api/files/folder", "POST", Api.json("name", name, "parentId", s.folder))
                newFolder = false
            },
            { newFolder = false },
        )
    shareFile?.let { ShareComposer(s, it) { shareFile = null } }
    moveFile?.let { choice ->
        MoveChooser(
            s,
            title = "移动到",
            exclude = if (choice.directory) setOf(choice.item.id()) else emptySet(),
            onMove = { target ->
                s.mutate(
                    "/api/files/" +
                        (if (choice.directory) "folder/" else "") +
                        choice.item.id() +
                        "/move",
                    "POST",
                    Api.json(if (choice.directory) "parentId" else "folderId", target),
                )
                moveFile = null
            },
            onDismiss = { moveFile = null },
        )
    }
    if (batchMoveOpen)
        MoveChooser(
            s,
            title = "移动 ${s.selected.size} 项到",
            exclude = s.selected.filter { it.startsWith("d") }.map { it.drop(1).toInt() }.toSet(),
            onMove = { target ->
                s.batchMove(target)
                batchMoveOpen = false
            },
            onDismiss = { batchMoveOpen = false },
        )
    if (profileEdit) EditProfile(s) { profileEdit = false }
    if (passwordEdit) PasswordDialog(s) { passwordEdit = false }
    if (emailEdit) EmailBindSheet(s) { emailEdit = false }
    sessions?.let { rows -> SessionSheet(s, rows) { sessions = null } }
    publicUser?.let { UserSheet(s, it) { publicUser = null } }
}

internal data class ConfirmAction(
    val title: String,
    val detail: String,
    val danger: Boolean = false,
    val action: () -> Unit,
)

internal data class FileChoice(
    val item: JSONObject,
    val directory: Boolean = false,
    val recycle: Boolean = false,
)

@Composable
internal fun DriveNavigation(tab: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        listOf(
                Triple(0, "云盘", Icons.Outlined.Folder),
                Triple(1, "传输", Icons.Outlined.SwapVert),
                Triple(2, "分享", Icons.Outlined.Link),
                Triple(3, "留言板", Icons.Outlined.Forum),
                Triple(4, "我的", Icons.Outlined.PersonOutline),
            )
            .forEach { (i, label, icon) ->
                NavigationBarItem(
                    selected = tab == i,
                    onClick = { onSelect(i) },
                    icon = { Icon(icon, label) },
                    label = { Text(label) },
                    colors =
                        NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                )
            }
    }
}
