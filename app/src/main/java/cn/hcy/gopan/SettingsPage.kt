package cn.hcy.gopan

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
internal fun SettingsPage(s: DriveState, onConfirm: (ConfirmAction) -> Unit) {
    val c = LocalContext.current
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var about by remember { mutableStateOf(false) }
    var checkTrigger by remember { mutableStateOf(0) }
    var logTrigger by remember { mutableStateOf(0) }
    var checking by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(checkTrigger) {
        if (checkTrigger == 0) return@LaunchedEffect
        checking = true
        found = withContext(Dispatchers.IO) { Updater.fetch(s.api) }
        checking = false
    }
    LaunchedEffect(logTrigger) {
        if (logTrigger == 0) return@LaunchedEffect
        found = withContext(Dispatchers.IO) { Updater.fetch(s.api) }
    }
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { PageHeader("设置", "按你的习惯使用云盘", { s.page = "main" }) }
        item {
            Panel {
                SectionTitle("外观")
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach {
                        (mode, label) ->
                        FilterChip(
                            selected = s.theme == mode,
                            onClick = { s.updateTheme(mode) },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("强调色", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    accents.forEachIndexed { i, color ->
                        Box(
                            Modifier.size(38.dp).background(color, CircleShape).clickable {
                                s.updateAccent(i)
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (s.accent == i)
                                Icon(
                                    Icons.Outlined.Check,
                                    null,
                                    Modifier.size(21.dp),
                                    tint = Color.White,
                                )
                        }
                    }
                }
            }
        }
        item {
            Panel {
                SectionTitle("传输与提醒")
                Spacer(Modifier.height(8.dp))
                SwitchSetting("仅非计费网络传输", "通常为 Wi-Fi，对之后启动或恢复的任务生效。", s.wifi) { s.updateWifi(it) }
                HorizontalDivider(
                    Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                SwitchSetting("留言与公告提醒", "系统约 15 分钟起检查一次，省电模式可能延迟。", s.notificationPolling) {
                    if (it && Build.VERSION.SDK_INT >= 33)
                        permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    s.setPolling(it)
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                Column {
                    SettingRow(Icons.Outlined.NotificationsNone, "通知权限", "传输进度、结果与留言提醒") {
                        c.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)
                        )
                    }
                    SettingRow(Icons.Outlined.DynamicFeed, "实时活动 / 流体云", "在系统通知设置中管理传输实时活动") {
                        val action =
                            runCatching {
                                    Settings::class
                                        .java
                                        .getField("ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS")
                                        .get(null) as String
                                }
                                .getOrDefault(
                                    "android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS"
                                )
                        val candidates =
                            listOf(
                                Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName),
                                Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS")
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName),
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName),
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${c.packageName}"),
                                ),
                            )
                        for (intent in candidates) {
                            if (runCatching { c.startActivity(intent) }.isSuccess) break
                        }
                    }
                    SettingRow(Icons.Outlined.BatterySaver, "后台与电池设置", "系统控制后台活动权限") {
                        c.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${c.packageName}"),
                            )
                        )
                    }
                }
            }
        }
        item {
            Panel {
                Text("后台传输的工作方式", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(12.dp))
                Text(
                    "传输期间显示进度通知，暂停、失败和完成后结束任务。Android 14+ 使用系统传输任务，旧版本使用前台服务。重启或强制停止后，可在传输中心恢复断点。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "OPPO / 一加可在系统应用设置中允许后台活动。流体云是否展示取决于系统版本、实时通知权限及系统判定。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                Column {
                    SettingRow(Icons.Outlined.Dns, "服务器", s.api.base()) {
                        onConfirm(
                            ConfirmAction("切换服务器？", "当前任务会暂停，旧会话会清除。之后可填写新的服务器地址。") {
                                s.launch {
                                    withContext(Dispatchers.IO) {
                                        Transfers.pauseOwner(c)
                                        s.api.clear()
                                    }
                                    s.user = null
                                    s.folder = null
                                    s.page = "main"
                                }
                            }
                        )
                    }
                    SettingRow(
                        Icons.Outlined.SystemUpdateAlt,
                        "检查更新",
                        "当前 v${Updater.currentName(c)} · 从服务器获取新安装包",
                    ) {
                        checkTrigger += 1
                    }
                    SettingRow(Icons.Outlined.History, "更新日志", "服务端发布的各版本说明") {
                        logTrigger += 1
                    }
                    SettingRow(Icons.Outlined.Info, "关于${s.siteName}", "${Updater.currentName(c)} · 原生 Android 客户端") {
                        about = true
                    }
                }
            }
        }
        if (s.user?.optString("role") == "admin")
            item {
                Panel {
                    SectionTitle("管理员")
                    Text(
                        "服务端的管理能力可通过原站点查看，移动端账户操作与服务端权限同步。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.api.base())))
                        }
                    ) {
                        Text("打开站点管理")
                    }
                }
            }
    }
    if (about)
        AlertDialog(
            onDismissRequest = { about = false },
            title = { Text(s.siteName) },
            text = {
                Text(
                    "原生 Kotlin + Jetpack Compose 客户端。\n\n文件、分享、回收站、留言板和账户与现有服务端同步。没有广告与统计 SDK。下载默认保存在应用私有空间，可另存到系统目录。卸载会清除本地文件。\n\n当前留言提醒为系统定期检查。厂商即时推送尚未接入。"
                )
            },
            confirmButton = { TextButton(onClick = { about = false }) { Text("完成") } },
        )
    if (checking) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("正在检查更新") },
            text = { Text("联系服务器…") },
            confirmButton = {},
        )
    }
    found?.let { rel ->
        if (logTrigger > 0) {
            UpdateChangelogDialog(release = rel) { logTrigger = 0 }
        } else if (Updater.isNewer(rel, Updater.currentCode(c))) {
            UpdateOfferDialog(release = rel) { found = null }
        } else {
            AlertDialog(
                onDismissRequest = { found = null },
                title = { Text("已是最新版本") },
                text = { Text("当前 v${Updater.currentName(c)} 就是服务器上的最新版本。") },
                confirmButton = { TextButton(onClick = { found = null }) { Text("好的") } },
            )
        }
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(5.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
