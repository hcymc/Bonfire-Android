package cn.hcy.gopan

import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.json.JSONObject

class PreviewActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        enableEdgeToEdge()
        val file = Api.parse(intent.getStringExtra("file"))
        setContent {
            val scope = rememberCoroutineScope()
            val drive = remember { DriveState(applicationContext, scope) }
            DriveTheme(drive) { FilePreview(this, file, drive) }
        }
    }
}

@Composable
private fun FilePreview(activity: PreviewActivity, file: JSONObject, s: DriveState) {
    val url = s.api.base() + "/api/files/${file.id()}/preview"
    var failure by remember { mutableStateOf("") }
    var ready by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(retry) {
        failure = ""
        ready = false
        try {
            val mime =
                withContext(Dispatchers.IO) {
                    val conn = s.api.open("/api/files/${file.id()}/preview", "HEAD")
                    try {
                        if (conn.responseCode !in 200..299) {
                            s.api.response(conn)
                            error("服务器不支持预览")
                        }
                        conn.contentType ?: "application/octet-stream"
                    } finally {
                        conn.disconnect()
                    }
                }
            type = mime
            ready = true
        } catch (e: Exception) {
            failure = e.message ?: "无法预览此文件"
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            file.optString("name"),
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            MainActivity.bytes(file.optLong("size")),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { activity.finish() }) {
                        Icon(Icons.Outlined.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { s.download(file) }) {
                        Icon(Icons.Outlined.Download, "下载文件")
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                failure.isNotBlank() ->
                    Column(
                        Modifier.align(Alignment.Center).padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Outlined.BrokenImage,
                            null,
                            Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(18.dp))
                        Text(failure)
                        Spacer(Modifier.height(18.dp))
                        Row {
                            TextButton(onClick = { retry++ }) { Text("重试") }
                            FilledTonalButton(onClick = { s.download(file) }) { Text("下载后打开") }
                        }
                    }
                !ready -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                type.startsWith("image/") -> ZoomImage(url, s)
                type.startsWith("video/") || type.startsWith("audio/") ->
                    MediaPreview(url, s, type.startsWith("audio/")) { failure = it }
                type.contains("pdf") -> DocumentPreview(file, s, true) { failure = it }
                type.startsWith("text/") || type.contains("json") ->
                    DocumentPreview(file, s, false) { failure = it }
                else ->
                    Empty(Icons.Outlined.InsertDriveFile, "此格式暂不支持预览", "可下载后使用对应应用打开。", "下载文件") {
                        s.download(file)
                    }
            }
            if (s.failure.isNotBlank())
                Text(
                    s.failure,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
                )
            if (s.notice.isNotBlank())
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(s.notice, Modifier.padding(16.dp))
                }
        }
    }
}

@Composable
private fun ZoomImage(url: String, s: DriveState) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 6f)
                offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model =
                ImageRequest.Builder(s.context)
                    .data(url)
                    .setHeader("Cookie", s.api.mediaHeaders()["Cookie"].orEmpty())
                    .build(),
            contentDescription = "图片预览，可双指缩放",
            contentScale = ContentScale.Fit,
            modifier =
                Modifier.fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
        )
        Text(
            "双指缩放与拖动",
            Modifier.align(Alignment.BottomCenter).padding(20.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
private fun MediaPreview(url: String, s: DriveState, audio: Boolean, onFailure: (String) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current
    val player =
        remember(url) {
            val http =
                OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()
            val source =
                OkHttpDataSource.Factory(http).setDefaultRequestProperties(s.api.mediaHeaders())
            ExoPlayer.Builder(s.context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(source))
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                    playWhenReady = true
                    addListener(
                        object : androidx.media3.common.Player.Listener {
                            override fun onPlayerError(
                                error: androidx.media3.common.PlaybackException
                            ) {
                                onFailure("播放失败：设备或服务器不支持该媒体格式")
                            }
                        }
                    )
                }
        }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        if (audio) {
            Icon(
                Icons.Outlined.GraphicEq,
                "音频",
                Modifier.align(Alignment.CenterHorizontally).size(88.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
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
                    .then(if (audio) Modifier.height(160.dp) else Modifier.weight(1f)),
        )
    }
}

@Composable
private fun DocumentPreview(
    file: JSONObject,
    s: DriveState,
    pdf: Boolean,
    onFailure: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var local by remember { mutableStateOf<File?>(null) }
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var index by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    LaunchedEffect(file.id()) {
        try {
            val result =
                withContext(Dispatchers.IO) {
                    val conn = s.api.open("/api/files/${file.id()}/preview", "GET")
                    try {
                        if (conn.responseCode != 200) {
                            s.api.response(conn)
                            error("无法预览")
                        }
                        val max = if (pdf) 64 * 1024 * 1024 else 2 * 1024 * 1024
                        val bytes = Api.readLimited(conn.inputStream, max)
                        if (pdf) {
                            val f = File.createTempFile("preview-", ".pdf", s.context.cacheDir)
                            f.writeBytes(bytes)
                            f
                        } else String(bytes, Charsets.UTF_8)
                    } finally {
                        conn.disconnect()
                    }
                }
            if (result is File) local = result else text = result as String
        } catch (e: Exception) {
            onFailure(e.message ?: "无法读取文档")
        }
    }
    LaunchedEffect(local, index) {
        local?.let { f ->
            try {
                val b =
                    withContext(Dispatchers.IO) {
                        android.graphics.pdf
                            .PdfRenderer(
                                ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                            )
                            .use { renderer ->
                                total = renderer.pageCount
                                renderer.openPage(index).use { page ->
                                    val width = 1200
                                    val bmp =
                                        android.graphics.Bitmap.createBitmap(
                                            width,
                                            (width * page.height.toFloat() / page.width).toInt(),
                                            android.graphics.Bitmap.Config.ARGB_8888,
                                        )
                                    bmp.eraseColor(android.graphics.Color.WHITE)
                                    page.render(
                                        bmp,
                                        null,
                                        null,
                                        android.graphics.pdf.PdfRenderer.Page
                                            .RENDER_MODE_FOR_DISPLAY,
                                    )
                                    bmp
                                }
                            }
                    }
                bitmap = b
            } catch (e: Exception) {
                onFailure(e.message ?: "PDF 渲染失败")
            }
        }
    }
    DisposableEffect(Unit) { onDispose { local?.delete() } }
    if (pdf)
        Column(Modifier.fillMaxSize()) {
            bitmap?.let {
                Image(
                    it.asImageBitmap(),
                    "PDF 第 ${index+1} 页",
                    Modifier.fillMaxWidth().weight(1f),
                    contentScale = ContentScale.Fit,
                )
            }
                ?: Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { index-- }, enabled = index > 0) {
                    Icon(Icons.Outlined.ChevronLeft, "上一页")
                }
                Text("${index+1} / $total")
                IconButton(onClick = { index++ }, enabled = index + 1 < total) {
                    Icon(Icons.Outlined.ChevronRight, "下一页")
                }
            }
        }
    else if (text.isBlank())
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    else
        SelectionContainer {
            Text(
                text,
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
}
