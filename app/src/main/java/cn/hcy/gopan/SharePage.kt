package cn.hcy.gopan

// 分享详情页：独立整页（不再是底部弹窗），顶部是分享者头像与主页入口，
// 中间是预览（图片 / 视频 / 音频 / PDF / 文本 / Markdown），底部一键下载——
// 下载进入统一的传输中心（Transfers），不再单独落盘。
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject

/** 打开 token 对应的分享详情（s.shareToken + s.page = "share"）。 */
@Composable
internal fun SharePage(s: DriveState) {
    val token = s.shareToken ?: run { s.page = "main"; return }
    val c = s.context

    var meta by remember(token) { mutableStateOf<JSONObject?>(null) }
    var metaError by remember(token) { mutableStateOf("") }
    var ticket by remember(token) { mutableStateOf("") }
    var password by remember(token) { mutableStateOf("") }
    var unlocking by remember(token) { mutableStateOf(false) }
    var unlockError by remember(token) { mutableStateOf("") }
    var profile by remember(token) { mutableStateOf<JSONObject?>(null) }
    var showUser by remember(token) { mutableStateOf(false) }

    suspend fun unlockWith(pw: String) {
        unlocking = true
        unlockError = ""
        try {
            ticket = s.unlockShare(token, pw.ifBlank { null })
        } catch (e: Exception) {
            unlockError = e.message ?: "解锁失败"
        } finally {
            unlocking = false
        }
    }

    LaunchedEffect(token) {
        metaError = ""
        try {
            val m = s.request("/api/public/shares/$token")
            meta = m
            profile =
                try {
                    s.request("/api/users/${android.net.Uri.encode(m.optString("owner"))}")
                        .optJSONObject("profile")
                } catch (e: Exception) {
                    null
                }
            if (m.optBoolean("available") && !m.optBoolean("needsPassword")) unlockWith("")
        } catch (e: Exception) {
            metaError = e.message ?: "无法读取分享"
        }
    }

    val name = meta?.optString("name").orEmpty()
    val lower = name.lowercase()
    val ext = if (lower.contains('.')) lower.substringAfterLast('.') else ""
    val unlocked = ticket.isNotBlank()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("分享详情", "来自 TA 的文件分享", { s.page = "main" })

        val m = meta
        when {
            metaError.isNotBlank() && m == null ->
                Empty(Icons.Outlined.LinkOff, "分享不可用", metaError)
            m == null ->
                Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            else -> {
                // 顶部：分享者
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(profile ?: Api.json("username", m.optString("owner")), 56.dp) {
                            showUser = true
                        }
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(
                                profile?.name() ?: m.optString("owner"),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Text(
                                "分享了一个文件 · 查看TA的主页",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
                Spacer(Modifier.height(14.dp))

                if (!m.optBoolean("available"))
                    Panel {
                        Text(
                            m.optString("reason").ifBlank { "分享已失效" },
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                else if (m.optBoolean("needsPassword") && !unlocked)
                    Panel {
                        SectionTitle("这条分享需要密码")
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            password,
                            { password = it },
                            label = { Text("访问密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (unlockError.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(unlockError, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { s.launch { unlockWith(password) } },
                            enabled = !unlocking && password.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (unlocking) "解锁中…" else "解锁")
                        }
                    }
                else {
                    // 预览
                    Panel(Modifier.fillMaxWidth()) {
                        SharePreview(s, token, ticket, m)
                    }
                    Spacer(Modifier.height(14.dp))
                    // 文件信息 + 下载
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    shareIcon(ext, m.optString("mime")),
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(
                                    name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    MainActivity.bytes(m.optLong("size")) +
                                        " · 已被访问 ${m.optInt("hits")} 次",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { s.downloadShare(token, m, ticket) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Download, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("下载到传输中心")
                        }
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    if (showUser && profile != null)
        UserSheet(s, Api.json("profile", profile)) { showUser = false }
}

private fun shareIcon(ext: String, mime: String): androidx.compose.ui.graphics.vector.ImageVector =
    when {
        mime.startsWith("image/") || ext in listOf("png", "jpg", "jpeg", "gif", "webp", "bmp") ->
            Icons.Outlined.Image
        mime.startsWith("video/") || ext in listOf("mp4", "m4v", "webm") -> Icons.Outlined.Videocam
        mime.startsWith("audio/") || ext in listOf("mp3", "wav", "ogg", "flac", "m4a") ->
            Icons.Outlined.Audiotrack
        ext == "pdf" -> Icons.Outlined.PictureAsPdf
        else -> Icons.Outlined.Description
    }

@Composable
private fun SharePreview(s: DriveState, token: String, ticket: String, meta: JSONObject) {
    val name = meta.optString("name")
    val lower = name.lowercase()
    val ext = if (lower.contains('.')) lower.substringAfterLast('.') else ""
    val mime = meta.optString("mime")
    val url = "${s.api.base()}/api/public/shares/$token/preview?ticket=$ticket"

    when {
        shareIcon(ext, mime) == Icons.Outlined.Image || mime.startsWith("image/") ->
            ShareImage(url, s)
        mime.startsWith("video/") || ext in listOf("mp4", "m4v", "webm") ->
            ShareMedia(url, s, audio = false)
        mime.startsWith("audio/") || ext in listOf("mp3", "wav", "ogg", "flac", "m4a") ->
            ShareMedia(url, s, audio = true)
        ext == "pdf" -> SharePdf(s, token, ticket)
        ext in listOf("md", "markdown") -> ShareMarkdown(s, url)
        isTextLike(ext, mime) -> ShareCode(s, url, ext)
        else ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.InsertDriveFile,
                    null,
                    Modifier.size(46.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "该类型不支持在线预览，可下载后查看",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
    }
}

private fun isTextLike(ext: String, mime: String): Boolean {
    if (mime.startsWith("text/")) return true
    return ext in listOf(
        "txt", "log", "json", "csv", "xml", "yml", "yaml", "ini", "conf",
        "js", "mjs", "ts", "css", "py", "java", "kt", "c", "h", "cpp", "rs",
        "go", "sh", "sql", "toml",
    )
}

@Composable
private fun ShareImage(url: String, s: DriveState) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 460.dp)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(s.context).data(url).crossfade(true).build(),
            contentDescription = "图片预览，可双指缩放",
            contentScale = ContentScale.Fit,
            modifier =
                Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 460.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
        )
    }
}

@Composable
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
private fun ShareMedia(url: String, s: DriveState, audio: Boolean) {
    val lifecycle = LocalLifecycleOwner.current
    var failed by remember(url) { mutableStateOf(false) }
    val player =
        remember(url) {
            val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()
            val source = OkHttpDataSource.Factory(http).setDefaultRequestProperties(s.api.mediaHeaders())
            ExoPlayer.Builder(s.context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(source))
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                    addListener(
                        object : Player.Listener {
                            override fun onPlayerError(error: PlaybackException) {
                                failed = true
                            }
                        }
                    )
                }
        }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause() else if (event == Lifecycle.Event.ON_RESUME && !failed) player.play()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    if (failed)
        Column(
            Modifier.fillMaxWidth().padding(vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.VideocamOff, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Text(
                "此设备无法在线播放（可能是 HEVC 编码）\n请下载后观看",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    else
        Column(Modifier.fillMaxWidth()) {
            if (audio) {
                Icon(
                    Icons.Outlined.GraphicEq,
                    "音频",
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp).size(72.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        this.player = player
                        useController = true
                        controllerShowTimeoutMs = if (audio) 0 else 3000
                    }
                },
                modifier =
                    Modifier.fillMaxWidth()
                        .then(if (audio) Modifier.height(150.dp) else Modifier.height(280.dp)),
            )
        }
}

@Composable
private fun SharePdf(s: DriveState, token: String, ticket: String) {
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var index by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var local by remember { mutableStateOf<File?>(null) }
    var failure by remember { mutableStateOf("") }

    LaunchedEffect(token, ticket) {
        try {
            local =
                withContext(Dispatchers.IO) {
                    val conn = s.api.open("/api/public/shares/$token/preview?ticket=$ticket", "GET")
                    try {
                        if (conn.responseCode != 200) {
                            s.api.response(conn)
                            error("无法读取 PDF")
                        }
                        val f = File.createTempFile("share-", ".pdf", s.context.cacheDir)
                        f.outputStream().use { out -> conn.inputStream.use { it.copyTo(out) } }
                        f
                    } finally {
                        conn.disconnect()
                    }
                }
        } catch (e: Exception) {
            failure = e.message ?: "PDF 加载失败"
        }
    }
    LaunchedEffect(local, index) {
        local?.let { f ->
            try {
                bitmap =
                    withContext(Dispatchers.IO) {
                        android.graphics.pdf.PdfRenderer(
                            ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                        ).use { renderer ->
                            total = renderer.pageCount
                            renderer.openPage(index).use { page ->
                                val width = 1200
                                val bmp = android.graphics.Bitmap.createBitmap(
                                    width,
                                    (width * page.height.toFloat() / page.width).toInt(),
                                    android.graphics.Bitmap.Config.ARGB_8888,
                                )
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bmp
                            }
                        }
                    }
            } catch (e: Exception) {
                failure = e.message ?: "PDF 渲染失败"
            }
        }
    }
    DisposableEffect(Unit) { onDispose { local?.delete() } }

    if (failure.isNotBlank())
        Text(failure, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
    else
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
                bitmap?.let {
                    Image(
                        it.asImageBitmap(),
                        "PDF 第 ${index + 1} 页",
                        Modifier.fillMaxWidth().height(360.dp),
                        contentScale = ContentScale.Fit,
                    )
                } ?: CircularProgressIndicator()
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { index-- }, enabled = index > 0) {
                    Icon(Icons.Outlined.ChevronLeft, "上一页")
                }
                Text("${index + 1} / $total", style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { index++ }, enabled = index + 1 < total) {
                    Icon(Icons.Outlined.ChevronRight, "下一页")
                }
            }
        }
}

@Composable
private fun ShareMarkdown(s: DriveState, url: String) {
    var raw by remember(url) { mutableStateOf<String?>(null) }
    var error by remember(url) { mutableStateOf("") }
    var source by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        try {
            raw =
                withContext(Dispatchers.IO) {
                    val conn = s.api.open(url.removePrefix(s.api.base()), "GET")
                    try {
                        if (conn.responseCode != 200) {
                            s.api.response(conn)
                            error("无法读取内容")
                        }
                        String(Api.readLimited(conn.inputStream, 2 shl 20), Charsets.UTF_8)
                    } finally {
                        conn.disconnect()
                    }
                }
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        }
    }
    val text = raw
    when {
        error.isNotBlank() -> Text(error, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
        text == null ->
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        else ->
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 4.dp)) {
                    FilterChip(
                        selected = !source,
                        onClick = { source = false },
                        label = { Text("渲染") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = source,
                        onClick = { source = true },
                        label = { Text("原文") },
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (source)
                    SelectionContainer {
                        Text(
                            text,
                            Modifier.fillMaxWidth().heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()).padding(4.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                else
                    Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        MarkdownBody(text)
                    }
            }
    }
}

/** 极简 Markdown 渲染：标题 / 粗体 / 行内代码 / 代码块 / 列表 / 引用。 */
@Composable
private fun MarkdownBody(text: String) {
    var inCode = false
    val codeBuffer = StringBuilder()
    for (line in text.split('\n')) {
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Text(
                        codeBuffer.toString().trimEnd(),
                        Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                codeBuffer.clear()
            }
            inCode = !inCode
            continue
        }
        if (inCode) {
            codeBuffer.append(line).append('\n')
            continue
        }
        val t = line.trimEnd()
        when {
            t.isBlank() -> Spacer(Modifier.height(8.dp))
            t.startsWith("### ") -> MdLine(t.removePrefix("### "), MaterialTheme.typography.titleSmall)
            t.startsWith("## ") -> MdLine(t.removePrefix("## "), MaterialTheme.typography.titleMedium)
            t.startsWith("# ") -> MdLine(t.removePrefix("# "), MaterialTheme.typography.headlineSmall)
            t.startsWith("> ") -> {
                Row(Modifier.padding(vertical = 3.dp)) {
                    Box(
                        Modifier.width(3.dp).height(18.dp)
                            .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    MdLine(t.removePrefix("> "), MaterialTheme.typography.bodyMedium, dim = true)
                }
            }
            t.startsWith("- ") || t.startsWith("* ") ->
                Row(Modifier.padding(vertical = 2.dp, horizontal = 4.dp)) {
                    Text("•", color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    MdLine(t.substring(2), MaterialTheme.typography.bodyMedium)
                }
            else -> MdLine(t, MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MdLine(
    raw: String,
    style: androidx.compose.ui.text.TextStyle,
    dim: Boolean = false,
) {
    Text(
        inlineMarkdown(raw),
        style = style,
        color =
            if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
}

/** 行内语法：**粗体**、`代码`、[文字](链接)→文字。 */
private fun inlineMarkdown(raw: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < raw.length) {
        when {
            raw.startsWith("**", i) -> {
                val end = raw.indexOf("**", i + 2)
                if (end > 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(raw.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(raw[i]); i++
                }
            }
            raw[i] == '`' -> {
                val end = raw.indexOf('`', i + 1)
                if (end > 0) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                        append(raw.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(raw[i]); i++
                }
            }
            raw[i] == '[' -> {
                val close = raw.indexOf(']', i)
                val open = if (close > 0) raw.indexOf('(', close) else -1
                val end = if (open > 0) raw.indexOf(')', open) else -1
                if (close > 0 && open == close + 1 && end > 0) {
                    append(raw.substring(i + 1, close))
                    i = end + 1
                } else {
                    append(raw[i]); i++
                }
            }
            else -> {
                append(raw[i]); i++
            }
        }
    }
}

/** 代码/文本预览：行内轻量高亮（注释 / 字符串 / 关键字 / 数字）。 */
@Composable
private fun ShareCode(s: DriveState, url: String, ext: String) {
    var text by remember(url) { mutableStateOf<String?>(null) }
    var error by remember(url) { mutableStateOf("") }
    LaunchedEffect(url) {
        try {
            text =
                withContext(Dispatchers.IO) {
                    val conn = s.api.open(url.removePrefix(s.api.base()), "GET")
                    try {
                        if (conn.responseCode != 200) {
                            s.api.response(conn)
                            error("无法读取内容")
                        }
                        String(Api.readLimited(conn.inputStream, 2 shl 20), Charsets.UTF_8)
                    } finally {
                        conn.disconnect()
                    }
                }
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        }
    }
    val body = text
    when {
        error.isNotBlank() -> Text(error, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
        body == null ->
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        else ->
            SelectionContainer {
                Text(
                    highlight(body, ext),
                    Modifier.fillMaxWidth().heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()).padding(4.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
    }
}

private val KEYWORDS = mapOf(
    "kt" to setOf("fun", "val", "var", "class", "object", "if", "else", "return", "import", "package", "when", "for", "while", "in", "null", "true", "false", "override", "private", "public", "internal", "suspend"),
    "java" to setOf("class", "interface", "public", "private", "protected", "static", "final", "void", "int", "long", "double", "boolean", "if", "else", "return", "new", "import", "package", "try", "catch", "true", "false", "null"),
    "py" to setOf("def", "class", "import", "from", "return", "if", "elif", "else", "for", "while", "in", "not", "and", "or", "None", "True", "False", "try", "except", "with", "as", "lambda", "self"),
    "js" to setOf("function", "const", "let", "var", "class", "return", "if", "else", "for", "while", "import", "export", "from", "new", "null", "undefined", "true", "false", "async", "await", "try", "catch"),
    "ts" to setOf("function", "const", "let", "var", "class", "interface", "type", "return", "if", "else", "for", "while", "import", "export", "from", "new", "null", "undefined", "true", "false", "async", "await", "try", "catch", "string", "number", "boolean"),
    "sh" to setOf("if", "then", "else", "fi", "for", "do", "done", "while", "echo", "export", "local", "return", "function"),
    "sql" to setOf("SELECT", "FROM", "WHERE", "INSERT", "INTO", "UPDATE", "DELETE", "CREATE", "TABLE", "INDEX", "JOIN", "LEFT", "GROUP", "ORDER", "BY", "LIMIT", "VALUES", "SET", "AND", "OR", "NOT", "NULL"),
)

/** 轻量语法高亮：注释 / 字符串 / 关键字 / 数字，够看即可。 */
private fun highlight(code: String, ext: String): AnnotatedString {
    val keywords = KEYWORDS[ext] ?: emptySet()
    val commentPrefix =
        when (ext) {
            "py", "sh", "yml", "yaml", "toml" -> "#"
            else -> "//"
        }
    val comment = SpanStyle(color = androidx.compose.ui.graphics.Color(0xFF8A8A96))
    val string = SpanStyle(color = androidx.compose.ui.graphics.Color(0xFF4CC38A))
    val keyword = SpanStyle(color = androidx.compose.ui.graphics.Color(0xFFB69BFF), fontWeight = FontWeight.SemiBold)
    val number = SpanStyle(color = androidx.compose.ui.graphics.Color(0xFFE0A44A))
    return buildAnnotatedString {
        for ((li, line) in code.split('\n').withIndex()) {
            if (li > 0) append('\n')
            val cIdx =
                if (ext == "sql") -1
                else line.indexOf(commentPrefix).let { i ->
                    if (commentPrefix == "//") {
                        // 避开字符串里的 "//"
                        var quote = 0
                        var best = i
                        for (j in 0 until (if (i >= 0) i else line.length)) if (line[j] == '"') quote++
                        if (quote % 2 == 1) best = -1
                        best
                    } else i
                }
            val codePart = if (cIdx >= 0) line.substring(0, cIdx) else line
            val commentPart = if (cIdx >= 0) line.substring(cIdx) else ""
            var i = 0
            while (i < codePart.length) {
                val ch = codePart[i]
                when {
                    ch == '"' || ch == '\'' -> {
                        val end = codePart.indexOf(ch, i + 1)
                        if (end > 0) {
                            withStyle(string) { append(codePart.substring(i, end + 1)) }
                            i = end + 1
                        } else {
                            withStyle(string) { append(codePart.substring(i)) }
                            i = codePart.length
                        }
                    }
                    ch.isDigit() -> {
                        var j = i
                        while (j < codePart.length && (codePart[j].isLetterOrDigit() || codePart[j] == '.')) j++
                        withStyle(number) { append(codePart.substring(i, j)) }
                        i = j
                    }
                    ch.isLetter() || ch == '_' -> {
                        var j = i
                        while (j < codePart.length && (codePart[j].isLetterOrDigit() || codePart[j] == '_')) j++
                        val word = codePart.substring(i, j)
                        if (word in keywords || (ext == "sql" && word.uppercase() in keywords)) {
                            withStyle(keyword) { append(word) }
                        } else append(word)
                        i = j
                    }
                    else -> {
                        append(ch); i++
                    }
                }
            }
            if (commentPart.isNotEmpty()) withStyle(comment) { append(commentPart) }
        }
    }
}
