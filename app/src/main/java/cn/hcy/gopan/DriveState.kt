package cn.hcy.gopan

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.rows(key: String): List<JSONObject> =
    optJSONArray(key)?.objects() ?: emptyList()

internal fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

internal fun JSONObject.name() = optString("display_name").ifBlank { optString("username") }

internal fun JSONObject.safeString(key: String) = if (isNull(key)) "" else optString(key)

internal val DriveState.siteName: String
    get() = site.safeString("name").ifBlank { "篝火云盘" }

internal fun JSONObject.id() = optInt("id")

internal class DriveState(val context: Context, val scope: CoroutineScope) {
    val api = Api.get(context)
    val prefs = context.getSharedPreferences("settings", 0)
    var user by mutableStateOf<JSONObject?>(null)
    /** 已绑定的邮箱；null 表示未绑定（老账号），用于提示与账户页展示。 */
    var email by mutableStateOf<String?>(null)
    var site by mutableStateOf(JSONObject())
    var tab by mutableIntStateOf(0)
    var folder by mutableStateOf<Int?>(null)
    var page by mutableStateOf("main")
    var shareToken by mutableStateOf<String?>(null)
    var crumbs by mutableStateOf<List<JSONObject>>(emptyList())
    var folders by mutableStateOf<List<JSONObject>>(emptyList())
    var files by mutableStateOf<List<JSONObject>>(emptyList())
    var shares by mutableStateOf<List<JSONObject>>(emptyList())
    var messages by mutableStateOf<List<JSONObject>>(emptyList())
    var recycled by mutableStateOf<List<JSONObject>>(emptyList())
    var tasks by mutableStateOf<List<JSONObject>>(emptyList())
    var loading by mutableStateOf(false)
    var booting by mutableStateOf(api.loggedIn())
    var failure by mutableStateOf("")
    var notice by mutableStateOf("")
    var query by mutableStateOf("")
    var category by mutableIntStateOf(0)
    var sort by mutableIntStateOf(prefs.getInt("sort", 0))
    var grid by mutableStateOf(prefs.getBoolean("grid", false))
    var selected by mutableStateOf<Set<String>>(emptySet())
    var theme by mutableStateOf(prefs.getString("theme", "system")!!)
    var accent by mutableIntStateOf(prefs.getInt("accent", 0))
    var notificationPolling by mutableStateOf(prefs.getBoolean("messages", false))
    var wifi by mutableStateOf(prefs.getBoolean("wifi", false))
    var busy by mutableStateOf(false)

    fun launch(showBusy: Boolean = false, action: suspend () -> Unit) {
        scope.launch {
            if (showBusy) busy = true
            failure = ""
            try {
                action()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e is Api.Failure && e.status == 401) {
                    withContext(Dispatchers.IO) { Transfers.pauseOwner(context) }
                    user = null
                    failure = "登录已过期，请重新登录。传输断点已保留。"
                } else failure = e.message ?: "连接中断，请重试"
            } finally {
                if (showBusy) busy = false
            }
        }
    }

    suspend fun request(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
    ): JSONObject = withContext(Dispatchers.IO) { api.request(path, method, body) }

    fun boot() {
        launch {
            if (api.loggedIn()) {
                try {
                    val me = request("/api/auth/me")
                    user = me.getJSONObject("user")
                    email = me.optString("email", "").ifBlank { null }
                    site = request("/api/site")
                    load()
                } finally {
                    booting = false
                }
            } else booting = false
        }
    }

    fun login(
        server: String,
        username: String,
        password: String,
        register: Boolean,
        email: String? = null,
        code: String? = null,
    ) {
        launch(true) {
            withContext(Dispatchers.IO) { api.setBase(server) }
            site = request("/api/site")
            if (register && !site.optBoolean("registrationOpen")) error("站点未开放注册")
            val body =
                Api.json(
                    "username",
                    username.trim(),
                    "password",
                    password,
                    "device",
                    "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}",
                )
            // 服务端开启邮箱强校验时，注册必须带上邮箱与验证码。
            if (register && email != null && code != null) {
                body.put("email", email.trim())
                body.put("code", code.trim())
            }
            val auth = request("/api/auth/" + if (register) "register" else "login", "POST", body)
            user = auth.getJSONObject("user")
            this@DriveState.email = auth.optString("email", "").ifBlank { null }
            prefs.edit().putString("username", username.trim()).apply()
            tab = 0
            page = "main"
            load()
            if (notificationPolling) MessageJob.configure(context, true)
        }
    }

    suspend fun load() {
        loading = true
        try {
            when (page) {
                "shares" -> shares = request("/api/shares").rows("shares")
                "recycle" -> {
                    val r = request("/api/files/recycle/list")
                    recycled =
                        r.rows("folders").map { it.put("_dir", true) } +
                            r.rows("files").map { it.put("_dir", false) }
                }
                else ->
                    when (tab) {
                        0 -> {
                            val r = request("/api/files" + (folder?.let { "?folderId=$it" } ?: ""))
                            crumbs = r.rows("breadcrumb")
                            folders = r.rows("folders")
                            files = r.rows("files")
                        }
                        1 -> refreshTasks()
                        2 -> shares = request("/api/shares").rows("shares")
                        3 -> {
                            site = request("/api/site")
                            messages = request("/api/guestbook?limit=100").rows("messages")
                        }
                        4 -> {
                            val me = request("/api/auth/me")
                            user = me.getJSONObject("user")
                            email = me.optString("email", "").ifBlank { null }
                        }
                    }
            }
        } finally {
            loading = false
        }
    }

    fun refresh() {
        launch { load() }
    }

    fun navigate(index: Int) {
        tab = index
        page = "main"
        selected = emptySet()
        failure = ""
        refresh()
    }

    /** 打开一个分享详情页（链接、公开主页、传输中心均可进入）。 */
    fun openShare(token: String) {
        shareToken = token.lowercase()
        page = "share"
        failure = ""
    }

    fun enter(id: Int?) {
        folder = id
        query = ""
        selected = emptySet()
        refresh()
    }

    fun back(): Boolean {
        if (selected.isNotEmpty()) {
            selected = emptySet()
            return true
        }
        if (page != "main") {
            page = "main"
            refresh()
            return true
        }
        if (tab == 0 && folder != null) {
            enter(crumbs.dropLast(1).lastOrNull()?.id())
            return true
        }
        if (tab != 0) {
            navigate(0)
            return true
        }
        return false
    }

    fun select(item: JSONObject, dir: Boolean) {
        val k = (if (dir) "d" else "f") + item.id()
        selected = if (k in selected) selected - k else selected + k
    }

    fun mutate(path: String, method: String, body: JSONObject? = null) {
        launch(true) {
            request(path, method, body)
            notice = "已完成"
            selected = emptySet()
            load()
            user = request("/api/auth/me").getJSONObject("user")
        }
    }

    suspend fun refreshTasks() {
        tasks =
            withContext(Dispatchers.IO) {
                val store = TaskStore.get(context)
                val pending =
                    context
                        .getSystemService(android.app.job.JobScheduler::class.java)
                        .allPendingJobs
                        .map { it.id }
                        .toSet()
                store.all(api.owner()).map { t ->
                    if (
                        t.optString("state") in listOf("running", "queued") &&
                            !Transfers.running.containsKey(t.id()) &&
                            t.id() !in pending &&
                            android.os.Build.VERSION.SDK_INT >= 34
                    ) {
                        store.state(t.id(), "paused", "任务已中断，点击继续可恢复断点")
                        store.task(t.id())
                    } else t
                }
            }
    }

    fun upload(uris: List<Uri>, persist: Boolean) {
        launch {
            val destination = folder
            val added =
                withContext(Dispatchers.IO) {
                    uris.map { uri ->
                        if (persist)
                            runCatching {
                                context.contentResolver.takePersistableUriPermission(
                                    uri,
                                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                )
                            }
                        Transfers.upload(context, uri, destination)
                    }
                }
            for (t in added) Transfers.schedule(context, t.id())
            notice = "已加入 ${added.size} 个上传任务"
            tab = 1
            page = "main"
            load()
        }
    }

    fun download(file: JSONObject) {
        launch {
            val t =
                withContext(Dispatchers.IO) {
                    Transfers.download(context, file, "/api/files/${file.id()}/download")
                }
            Transfers.schedule(context, t.id())
            notice = "已加入下载队列"
            refreshTasks()
        }
    }

    /** 分享详情：解锁拿到 ticket（公开分享服务端直接发）。 */
    suspend fun unlockShare(token: String, password: String?): String {
        val body = Api.json("password", password ?: "")
        return request("/api/public/shares/$token/unlock", "POST", body).getString("ticket")
    }

    /** 把分享文件加入下载队列（走统一的传输中心，不再单独落盘）。 */
    fun downloadShare(token: String, meta: JSONObject, ticket: String) {
        launch {
            val file =
                Api.json(
                    "id", 0,
                    "name", meta.optString("name"),
                    "mime", meta.optString("mime", "application/octet-stream"),
                    "size", meta.optLong("size"),
                )
            val t =
                withContext(Dispatchers.IO) {
                    Transfers.download(
                        context,
                        file,
                        "/api/public/shares/$token/file?ticket=$ticket",
                    )
                }
            Transfers.schedule(context, t.id())
            notice = "已加入传输中心"
            refreshTasks()
        }
    }

    fun resume(t: JSONObject) {
        launch {
            Transfers.schedule(context, t.id())
            refreshTasks()
        }
    }

    fun pause(t: JSONObject) {
        launch {
            withContext(Dispatchers.IO) { Transfers.pause(context, t.id()) }
            refreshTasks()
        }
    }

    fun cancel(t: JSONObject) {
        launch {
            withContext(Dispatchers.IO) { Transfers.cancel(context, t.id()) }
            refreshTasks()
        }
    }

    fun logout() {
        email = null
        launch(true) {
            withContext(Dispatchers.IO) { Transfers.pauseOwner(context) }
            request("/api/auth/logout", "POST")
            withContext(Dispatchers.IO) { api.clear() }
            MessageJob.configure(context, false)
            user = null
            folder = null
            files = emptyList()
            tasks = emptyList()
        }
    }

    fun updateTheme(mode: String) {
        theme = mode
        prefs.edit().putString("theme", mode).apply()
    }

    fun updateAccent(index: Int) {
        accent = index
        prefs.edit().putInt("accent", index).apply()
    }

    fun updateGrid(value: Boolean) {
        grid = value
        prefs.edit().putBoolean("grid", value).apply()
    }

    fun updateSort(value: Int) {
        sort = value
        prefs.edit().putInt("sort", value).apply()
    }

    fun updateWifi(value: Boolean) {
        wifi = value
        prefs.edit().putBoolean("wifi", value).apply()
    }

    fun setPolling(value: Boolean) {
        notificationPolling = value
        prefs.edit().putBoolean("messages", value).apply()
        MessageJob.configure(context, value)
    }

    fun batchMove(target: Int?) {
        val keys = selected.toList()
        launch(true) {
            var count = 0
            for (key in keys) {
                val dir = key.startsWith("d")
                val id = key.drop(1).toInt()
                request(
                    "/api/files/" + (if (dir) "folder/" else "") + id + "/move",
                    "POST",
                    Api.json(if (dir) "parentId" else "folderId", target),
                )
                count++
            }
            selected = emptySet()
            notice = "已移动 $count 项"
            load()
        }
    }

    fun batch(delete: Boolean) {
        val keys = selected.toList()
        launch(true) {
            var count = 0
            for (key in keys) {
                val dir = key.startsWith("d")
                val id = key.drop(1).toInt()
                if (delete) request("/api/files/" + (if (dir) "folder/" else "") + id, "DELETE")
                else if (!dir) {
                    files
                        .find { it.id() == id }
                        ?.let {
                            val t =
                                withContext(Dispatchers.IO) {
                                    Transfers.download(context, it, "/api/files/$id/download")
                                }
                            Transfers.schedule(context, t.id())
                        }
                }
                count++
            }
            selected = emptySet()
            notice = "已处理 $count 项"
            load()
        }
    }

    fun uploadAvatar(uri: Uri) {
        launch(true) {
            val payload =
                withContext(Dispatchers.IO) {
                    val source = context.contentResolver.openInputStream(uri) ?: error("无法读取图片")
                    val raw = Api.readLimited(source, 32 * 1024 * 1024)
                    val opts =
                        android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, opts)
                    if (opts.outWidth <= 0) error("无法识别图片格式")
                    opts.inSampleSize = 1
                    while (
                        opts.outWidth / opts.inSampleSize > 1536 ||
                            opts.outHeight / opts.inSampleSize > 1536
                    ) opts.inSampleSize *= 2
                    opts.inJustDecodeBounds = false
                    val bitmap =
                        android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, opts)
                            ?: error("无法解码图片")
                    val side = minOf(bitmap.width, bitmap.height)
                    val cropped =
                        android.graphics.Bitmap.createBitmap(
                            bitmap,
                            (bitmap.width - side) / 2,
                            (bitmap.height - side) / 2,
                            side,
                            side,
                        )
                    val scaled = android.graphics.Bitmap.createScaledBitmap(cropped, 512, 512, true)
                    val out = java.io.ByteArrayOutputStream()
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                    val bytes = out.toByteArray()
                    if (
                        bytes.size >
                            site.optJSONObject("limits")?.optInt("avatarMaxBytes", 2097152)
                                ?: 2097152
                    )
                        error("头像超过站点大小限制")
                    val conn = api.open("/api/me/avatar", "POST")
                    try {
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", "application/octet-stream")
                        conn.setFixedLengthStreamingMode(bytes.size)
                        conn.outputStream.use { it.write(bytes) }
                        api.response(conn)
                    } finally {
                        conn.disconnect()
                        if (scaled !== cropped) scaled.recycle()
                        if (cropped !== bitmap) cropped.recycle()
                        bitmap.recycle()
                    }
                }
            user = payload.getJSONObject("profile")
            notice = "头像已更新"
        }
    }
}
