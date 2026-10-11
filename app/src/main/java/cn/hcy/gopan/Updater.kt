package cn.hcy.gopan

// 安卓更新通道：/api/releases/android（公开只读）返回 manifest，安装包从
// /releases/<file> 下载。服务端已校验 manifest 与磁盘字节一致；客户端下载后
// 再用 sha256 复核一遍才交给系统安装器。进度对话框与更新日志也在这里。
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object Updater {
    /** Release manifest from the server, or null when unreachable / nothing published. */
    internal fun fetch(api: Api): JSONObject? = try {
        api.request("/api/releases/android").optJSONObject("release")
    } catch (e: Exception) {
        null
    }

    fun currentCode(c: Context): Int = try {
        val pi = c.packageManager.getPackageInfo(c.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else pi.versionCode
    } catch (e: Exception) {
        0
    }

    fun currentName(c: Context): String = try {
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    fun isNewer(rel: JSONObject, installedCode: Int): Boolean = rel.optInt("versionCode") > installedCode

    /** Stream the APK into the app's downloads dir, verifying size + sha256 on the way. */
    internal fun download(c: Context, api: Api, rel: JSONObject, onProgress: (Int) -> Unit): File {
        val dir = File(c.filesDir, "downloads")
        dir.mkdirs()
        val dest = File(dir, "update-" + rel.optString("version") + ".apk")
        val conn = URL(api.base() + "/releases/" + rel.optString("file")).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 45000
        val total = rel.optLong("size")
        try {
            conn.getInputStream().use { input ->
                dest.outputStream().use { out ->
                    val md = MessageDigest.getInstance("SHA-256")
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        md.update(buf, 0, n)
                        done += n
                        val now = SystemClock.elapsedRealtime()
                        if (now - last > 400) {
                            last = now
                            if (total > 0) onProgress(((done * 100) / total).toInt())
                        }
                    }
                    val hex = md.digest().joinToString("") { "%02x".format(it) }
                    val want = rel.optString("sha256").lowercase()
                    if (want.isNotEmpty() && hex != want) throw java.io.IOException("安装包校验失败，请重试")
                    if (total > 0 && done != total) throw java.io.IOException("下载不完整，请重试")
                    onProgress(100)
                }
            }
        } catch (e: Exception) {
            dest.delete()
            throw e
        }
        return dest
    }

    /** Hand the apk to the system installer. LocalFiles serves files/downloads/<name>. */
    fun install(c: Context, apk: File) {
        val uri = Uri.Builder().scheme("content").authority("cn.hcy.gopan.files").encodedPath(apk.name).build()
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        c.startActivity(i)
    }
}

@Composable
fun UpdateChangelogDialog(release: JSONObject, onClose: () -> Unit) {
    val history = release.optJSONArray("history")
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("更新日志") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (history == null || history.length() == 0) {
                    Text(release.optString("notes").ifBlank { "暂无更新说明。" })
                } else {
                    for (i in 0 until history.length()) {
                        val h = history.optJSONObject(i) ?: continue
                        Text("v" + h.optString("version"), style = MaterialTheme.typography.titleSmall)
                        if (h.optString("date").isNotBlank()) {
                            Text(
                                h.optString("date"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            h.optString("notes").ifBlank { "（无说明）" },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("完成") } },
    )
}

@Composable
fun UpdateOfferDialog(release: JSONObject, onClose: () -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var pct by remember { mutableStateOf(-1) }
    var err by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (pct < 0) onClose() },
        title = { Text("发现新版本 v" + release.optString("version")) },
        text = {
            Column {
                if (err.isNotBlank()) {
                    Text(err, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    release.optString("notes").ifBlank { "暂无更新说明。" },
                    modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                )
                if (pct >= 0) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((pct.coerceIn(0, 100)) / 100f)
                                .height(6.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pct < 0,
                onClick = {
                    pct = 0
                    err = ""
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val apk = Updater.download(c, Api.get(c), release) { p -> pct = p }
                                Updater.install(c, apk)
                            }
                            onClose()
                        } catch (e: Exception) {
                            err = e.message ?: "下载失败，请重试"
                            pct = -1
                        }
                    }
                },
            ) { Text(if (pct < 0) "下载并安装" else if (pct >= 100) "启动安装" else "下载中 $pct%") }
        },
        dismissButton = {
            TextButton(enabled = pct < 0, onClick = onClose) { Text("以后再说") }
        },
    )
}

// 冷启动静默检查一次：有新版本且这个版本还没提示过才弹（prefs 记忆，避免反复打扰）。
@Composable
internal fun UpdateGate(s: DriveState) {
    if (s.user == null) return
    val c = LocalContext.current
    var rel by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(s.user?.optLong("id")) {
        val seen = s.prefs.getLong("update_seen_code", 0)
        val fresh = withContext(Dispatchers.IO) { Updater.fetch(s.api) } ?: return@LaunchedEffect
        if (Updater.isNewer(fresh, Updater.currentCode(c)) && fresh.optInt("versionCode") > seen) rel = fresh
    }
    rel?.let { r ->
        UpdateOfferDialog(release = r, onClose = {
            s.prefs.edit().putLong("update_seen_code", r.optInt("versionCode").toLong()).apply()
            rel = null
        })
    }
}
