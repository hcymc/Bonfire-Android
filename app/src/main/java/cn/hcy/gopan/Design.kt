package cn.hcy.gopan

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.json.JSONObject

internal val Ember = Color(0xFFD95D39)
internal val accents = listOf(Ember, Color(0xFF3675A9), Color(0xFF527F65), Color(0xFF8065AA))

@Composable
internal fun DriveTheme(state: DriveState, content: @Composable () -> Unit) {
    val dark =
        when (state.theme) {
            "light" -> false
            "dark" -> true
            else -> isSystemInDarkTheme()
        }
    val accent by
        animateColorAsState(accents[state.accent.coerceIn(0, 3)], tween(220), label = "accent")
    val container =
        accent.copy(alpha = 1f).let {
            lerp(it, if (dark) Color(0xFF1E2521) else Color.White, if (dark) .65f else .88f)
        }
    val colors =
        if (dark)
            darkColorScheme(
                primaryContainer = container,
                onPrimaryContainer = Color.White,
                secondary = accent,
                secondaryContainer = container,
                onSecondaryContainer = Color.White,
                tertiary = accent,
                tertiaryContainer = container,
                surfaceTint = accent,
                primary =
                    accent.copy(
                        red = (accent.red + .2f).coerceAtMost(1f),
                        green = (accent.green + .2f).coerceAtMost(1f),
                        blue = (accent.blue + .2f).coerceAtMost(1f),
                    ),
                background = Color(0xFF141916),
                surface = Color(0xFF1E2521),
                surfaceContainer = Color(0xFF252D27),
                surfaceContainerLow = Color(0xFF1C231F),
                surfaceContainerHighest = Color(0xFF303B33),
                surfaceVariant = Color(0xFF303B33),
                outline = Color(0xFF7B877E),
                onSurface = Color(0xFFE9EEE7),
                onSurfaceVariant = Color(0xFF9EA99F),
                outlineVariant = Color(0xFF374139),
            )
        else
            lightColorScheme(
                primary = accent,
                onPrimary = Color.White,
                primaryContainer = container,
                onPrimaryContainer = accent,
                secondary = accent,
                onSecondary = Color.White,
                secondaryContainer = container,
                onSecondaryContainer = accent,
                tertiary = accent,
                tertiaryContainer = container,
                onTertiaryContainer = accent,
                surfaceTint = accent,
                background = Color(0xFFF7F8F5),
                surface = Color.White,
                surfaceContainer = Color(0xFFF0F3EC),
                surfaceContainerLow = Color(0xFFFAFBF8),
                surfaceContainerHighest = Color(0xFFE8EDE5),
                surfaceVariant = Color(0xFFE8EDE5),
                outline = Color(0xFF7B877E),
                onSurface = Color(0xFF23302A),
                onSurfaceVariant = Color(0xFF7B877E),
                outlineVariant = Color(0xFFE3E9DF),
            )
    MaterialTheme(
        colorScheme = colors,
        typography =
            Typography(
                headlineLarge =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp,
                    ),
                headlineMedium =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                titleLarge =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                titleMedium =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                bodyLarge =
                    androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
                bodyMedium =
                    androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
                labelSmall =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
            ),
        shapes =
            Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(24.dp),
            ),
        content = content,
    )
}

internal data class Kind(val label: String, val icon: ImageVector, val tint: Color)

internal fun kind(file: JSONObject, folder: Boolean = false): Kind {
    if (folder) return Kind("文件夹", Icons.Outlined.Folder, Color(0xFFCD9A40))
    val name = file.optString("name").lowercase()
    val ext = name.substringAfterLast('.')
    return when (ext) {
        "png",
        "jpg",
        "jpeg",
        "webp",
        "gif",
        "bmp",
        "heic",
        "avif" -> Kind("图片", Icons.Outlined.Image, Color(0xFF5089AA))
        "mp4",
        "mov",
        "webm",
        "mkv",
        "avi" -> Kind("视频", Icons.Outlined.Movie, Color(0xFF9878B9))
        "mp3",
        "flac",
        "wav",
        "m4a",
        "aac",
        "ogg" -> Kind("音频", Icons.Outlined.AudioFile, Color(0xFF579A8F))
        "pdf" -> Kind("PDF", Icons.Outlined.PictureAsPdf, Color(0xFFD56763))
        "zip",
        "rar",
        "7z",
        "tar",
        "gz" -> Kind("压缩包", Icons.Outlined.Archive, Color(0xFFB99757))
        "apk" -> Kind("安装包", Icons.Outlined.Android, Color(0xFF74A566))
        "doc",
        "docx",
        "xls",
        "xlsx",
        "ppt",
        "pptx" -> Kind("文档", Icons.Outlined.Description, Color(0xFF6E88BE))
        "kt",
        "java",
        "py",
        "rs",
        "go",
        "json",
        "yaml",
        "xml",
        "ts" -> Kind("代码", Icons.Outlined.Code, Color(0xFF559AA9))
        else -> Kind("文档", Icons.Outlined.InsertDriveFile, Color(0xFF869487))
    }
}

@Composable
internal fun Avatar(person: JSONObject?, size: Dp = 44.dp, onClick: (() -> Unit)? = null) {
    val name = person?.name().orEmpty()
    val url = person?.safeString("avatar_url").orEmpty()
    val c = LocalContext.current
    val api = Api.get(c)
    var failed by remember(url) { mutableStateOf(false) }
    Box(
        Modifier.size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).ifBlank { "火" },
            color = MaterialTheme.colorScheme.primary,
            fontSize = (size.value * .36).sp,
            fontWeight = FontWeight.Bold,
        )
        if (url.isNotEmpty() && !failed)
            AsyncImage(
                model =
                    ImageRequest.Builder(c)
                        .data(api.base() + url)
                        .setHeader("Cookie", api.mediaHeaders()["Cookie"].orEmpty())
                        .crossfade(true)
                        .build(),
                contentDescription = "${name}的头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true },
            )
    }
}

@Composable
internal fun FileVisual(file: JSONObject, folder: Boolean, size: Dp = 48.dp) {
    val k = kind(file, folder)
    val c = LocalContext.current
    val api = Api.get(c)
    var failed by remember(file.id()) { mutableStateOf(false) }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(k.tint.copy(alpha = .1f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!folder && k.label == "图片" && !failed)
            AsyncImage(
                model =
                    ImageRequest.Builder(c)
                        .data(api.base() + "/api/files/${file.id()}/preview")
                        .setHeader("Cookie", api.mediaHeaders()["Cookie"].orEmpty())
                        .size(256)
                        .crossfade(true)
                        .build(),
                contentDescription = file.optString("name"),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true },
            )
        else Icon(k.icon, null, Modifier.size(size * .48f), tint = k.tint)
    }
}

@Composable
internal fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)),
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
internal fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null)
            TextButton(onClick = onAction) {
                Text(action, style = MaterialTheme.typography.labelMedium)
            }
    }
}

@Composable
internal fun Empty(
    icon: ImageVector,
    title: String,
    detail: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(88.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(22.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            detail,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
internal fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent =
            if (subtitle.isNotEmpty()) {
                { Text(subtitle, style = MaterialTheme.typography.bodyMedium) }
            } else null,
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = {
            Icon(
                Icons.Outlined.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
