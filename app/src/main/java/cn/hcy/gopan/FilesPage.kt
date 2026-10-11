package cn.hcy.gopan

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import org.json.JSONObject

@Composable
internal fun FilesPage(
    s: DriveState,
    onUpload: () -> Unit,
    onNewFolder: () -> Unit,
    onFile: (JSONObject, Boolean) -> Unit,
    onMenu: (JSONObject, Boolean) -> Unit,
    onBatchMove: () -> Unit,
    onBatchDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var sortMenu by remember { mutableStateOf(false) }
    val entries =
        remember(s.files, s.folders, s.query, s.category, s.sort) {
            val dirs =
                if (s.category == 0)
                    s.folders
                        .filter { it.optString("name").contains(s.query, true) }
                        .sortedBy { it.optString("name").lowercase() }
                        .map { FileChoice(it, true) }
                else emptyList()
            val files =
                s.files
                    .filter { f ->
                        f.optString("name").contains(s.query, true) &&
                            when (s.category) {
                                1 -> kind(f).label == "图片"
                                2 -> kind(f).label == "视频"
                                3 -> kind(f).label == "音频"
                                4 -> kind(f).label in listOf("文档", "PDF", "代码")
                                else -> true
                            }
                    }
                    .sortedWith(
                        when (s.sort) {
                            1 -> compareByDescending { it.optString("updated_at") }
                            2 -> compareByDescending { it.optLong("size") }
                            else -> compareBy { it.optString("name").lowercase() }
                        }
                    )
                    .map { FileChoice(it) }
            dirs + files
        }
    Column(Modifier.fillMaxSize()) {
        if (s.selected.isNotEmpty())
            Surface(color = colors.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { s.selected = emptySet() }) {
                        Icon(Icons.Outlined.Close, "取消选择")
                    }
                    Text("已选择 ${s.selected.size} 项", Modifier.weight(1f))
                    IconButton(onClick = onBatchMove) {
                        Icon(Icons.Outlined.DriveFileMove, "移动所选项目")
                    }
                    IconButton(onClick = { s.batch(false) }) {
                        Icon(Icons.Outlined.Download, "下载所选文件")
                    }
                    IconButton(onClick = onBatchDelete) {
                        Icon(Icons.Outlined.DeleteOutline, "移入回收站")
                    }
                }
            }
        Crossfade(targetState = s.grid, animationSpec = tween(180), label = "viewMode") { grid
            ->
            if (grid)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding =
                        PaddingValues(
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                            bottom = 104.dp,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) {
                    item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                        FilesHeader(
                            s,
                            entries.size,
                            onUpload = onUpload,
                            onNewFolder = onNewFolder,
                            sortMenuExpanded = sortMenu,
                            onSortMenu = { sortMenu = it },
                        )
                    }
                    if (entries.isEmpty() && s.loading) {
                        item(key = "placeholder", span = { GridItemSpan(maxLineSpan) }) {
                            FileListPlaceholder()
                        }
                    } else if (entries.isEmpty()) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                            Empty(
                                Icons.Outlined.FolderOpen,
                                if (s.query.isNotBlank()) "没有找到匹配文件" else "还没有文件",
                                if (s.query.isNotBlank()) "试试其他关键词或分类。" else "上传照片、文档和灵感，随时随地取用。",
                                if (s.query.isBlank()) "上传第一份文件" else null,
                                onUpload,
                            )
                        }
                    } else {
                        items(entries, key = { (if (it.directory) "d" else "f") + it.item.id() }) {
                            f ->
                            val key = (if (f.directory) "d" else "f") + f.item.id()
                            FileTile(
                                f,
                                key in s.selected,
                                modifier = Modifier.animateItem(),
                                onClick = { onFile(f.item, f.directory) },
                                onLong = { s.select(f.item, f.directory) },
                                onMenu = { onMenu(f.item, f.directory) },
                            )
                        }
                    }
                }
            else
                LazyColumn(
                    contentPadding =
                        PaddingValues(
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                            bottom = 104.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) {
                    item(key = "header") {
                        FilesHeader(
                            s,
                            entries.size,
                            onUpload = onUpload,
                            onNewFolder = onNewFolder,
                            sortMenuExpanded = sortMenu,
                            onSortMenu = { sortMenu = it },
                        )
                    }
                    if (entries.isEmpty() && s.loading) {
                        item(key = "placeholder") { FileListPlaceholder() }
                    } else if (entries.isEmpty()) {
                        item(key = "empty") {
                            Empty(
                                Icons.Outlined.FolderOpen,
                                if (s.query.isNotBlank()) "没有找到匹配文件" else "还没有文件",
                                if (s.query.isNotBlank()) "试试其他关键词或分类。" else "上传照片、文档和灵感，随时随地取用。",
                                if (s.query.isBlank()) "上传第一份文件" else null,
                                onUpload,
                            )
                        }
                    } else {
                        items(entries, key = { (if (it.directory) "d" else "f") + it.item.id() }) {
                            f ->
                            val key = (if (f.directory) "d" else "f") + f.item.id()
                            FileRow(
                                f,
                                key in s.selected,
                                modifier = Modifier.animateItem(),
                                onClick = { onFile(f.item, f.directory) },
                                onLong = { s.select(f.item, f.directory) },
                                onMenu = { onMenu(f.item, f.directory) },
                            )
                            HorizontalDivider(
                                color = colors.outlineVariant.copy(alpha = .65f),
                                modifier = Modifier.padding(start = 64.dp),
                            )
                        }
                    }
                }
        }
    }
}

/** 顶部信息区：跟列表一起滚动，给文件留出全部空间。多选时只保留钉住的选择栏。 */
@Composable
private fun FilesHeader(
    s: DriveState,
    itemCount: Int,
    onUpload: () -> Unit,
    onNewFolder: () -> Unit,
    sortMenuExpanded: Boolean,
    onSortMenu: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 2.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(s.siteName, style = MaterialTheme.typography.labelMedium, color = colors.primary)
                Spacer(Modifier.height(5.dp))
                Text(
                    if (s.folder == null) "我的文件"
                    else s.crumbs.lastOrNull()?.optString("name") ?: "文件夹",
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { s.refresh() }) { Icon(Icons.Outlined.Refresh, "刷新") }
            Avatar(s.user, 46.dp) { s.navigate(4) }
        }
        if (s.selected.isEmpty()) {
            OutlinedTextField(
                value = s.query,
                onValueChange = { s.query = it },
                placeholder = { Text("搜索当前文件夹", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon =
                    if (s.query.isNotEmpty()) {
                        {
                            IconButton(onClick = { s.query = "" }) {
                                Icon(Icons.Outlined.Close, "清除搜索")
                            }
                        }
                    } else null,
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = colors.outlineVariant,
                        focusedBorderColor = colors.primary,
                        unfocusedContainerColor = colors.surface,
                    ),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("全部", "图片", "视频", "音频", "文档").forEachIndexed { i, label ->
                    FilterChip(
                        selected = s.category == i,
                        onClick = { s.category = i },
                        label = { Text(label) },
                        shape = RoundedCornerShape(12.dp),
                        border =
                            FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = s.category == i,
                                borderColor = colors.outlineVariant,
                            ),
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primaryContainer,
                                selectedLabelColor = colors.primary,
                            ),
                    )
                }
            }
        }
        if (s.folder != null) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { s.enter(null) }) { Text("全部文件") }
                s.crumbs.forEach { crumb ->
                    Icon(
                        Icons.Outlined.ChevronRight,
                        null,
                        Modifier.size(16.dp),
                        tint = colors.onSurfaceVariant,
                    )
                    TextButton(onClick = { s.enter(crumb.id()) }) { Text(crumb.optString("name")) }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$itemCount 个项目",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNewFolder) { Icon(Icons.Outlined.CreateNewFolder, "新建文件夹") }
            Box {
                IconButton(onClick = { onSortMenu(true) }) { Icon(Icons.Outlined.Sort, "排序") }
                DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { onSortMenu(false) }) {
                    listOf("名称 A → Z", "最近修改优先", "文件大小优先").forEachIndexed { i, label ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                s.updateSort(i)
                                onSortMenu(false)
                            },
                            trailingIcon =
                                if (s.sort == i) {
                                    { Icon(Icons.Outlined.Check, null) }
                                } else null,
                        )
                    }
                }
            }
            IconButton(onClick = { s.updateGrid(!s.grid) }) { ViewModeIcon(s.grid) }
        }
    }
}

@Composable
internal fun FileRow(
    f: FileChoice,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLong: () -> Unit = {},
    onMenu: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val background by
        animateColorAsState(
            if (selected) c.primaryContainer else Color.Transparent,
            label = "selection",
        )
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(start = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            FileVisual(f.item, f.directory)
            if (selected)
                Icon(
                    Icons.Outlined.CheckCircle,
                    "已选择",
                    Modifier.align(Alignment.BottomEnd)
                        .size(20.dp)
                        .background(c.surface, CircleShape),
                    tint = c.primary,
                )
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                f.item.optString("name"),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            val timestamp =
                MainActivity.time(
                    f.item.optString(
                        if (f.recycle) "deleted_at" else "updated_at",
                        f.item.optString("created_at"),
                    ),
                ).take(10)
            Text(
                (if (f.directory) "文件夹" else MainActivity.bytes(f.item.optLong("size"))) +
                    if (timestamp.isNotBlank()) "  ·  $timestamp" else "",
                style = MaterialTheme.typography.labelSmall,
                color = c.onSurfaceVariant,
            )
        }
        IconButton(onClick = onMenu) {
            Icon(Icons.Outlined.MoreVert, "更多操作", tint = c.onSurfaceVariant)
        }
    }
}

@Composable
private fun FileTile(
    f: FileChoice,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLong: () -> Unit,
    onMenu: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val background by
        animateColorAsState(
            if (selected) c.primaryContainer else c.surface,
            label = "tileSelection",
        )
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = background,
        border =
            BorderStroke(1.dp, if (selected) c.primary else c.outlineVariant.copy(alpha = .55f)),
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLong),
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.fillMaxWidth().height(86.dp)) {
                FileVisual(f.item, f.directory, 76.dp)
                IconButton(
                    onClick = onMenu,
                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
                ) {
                    Icon(
                        if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.MoreHoriz,
                        "更多操作",
                        Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                f.item.optString("name"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                if (f.directory) "文件夹" else MainActivity.bytes(f.item.optLong("size")),
                style = MaterialTheme.typography.labelSmall,
                color = c.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ViewModeIcon(grid: Boolean) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(24.dp).semantics { contentDescription = if (grid) "列表视图" else "网格视图" }) {
        val unit = size.width / 24f
        if (grid)
            for (y in listOf(5f, 12f, 19f)) {
                drawRoundRect(color, Offset(3 * unit, (y - 1.5f) * unit), Size(3 * unit, 3 * unit))
                drawLine(
                    color,
                    Offset(9 * unit, y * unit),
                    Offset(21 * unit, y * unit),
                    2 * unit,
                    StrokeCap.Round,
                )
            }
        else
            for (x in listOf(3f, 14f)) for (y in listOf(3f, 14f)) {
                drawRoundRect(
                    color,
                    Offset(x * unit, y * unit),
                    Size(7 * unit, 7 * unit),
                    androidx.compose.ui.geometry.CornerRadius(unit, unit),
                )
            }
    }
}

@Composable
private fun FileListPlaceholder() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(180)
        visible = true
    }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            repeat(4) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainer,
                                RoundedCornerShape(14.dp),
                            )
                    )
                    Column(
                        Modifier.weight(1f).padding(start = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier.fillMaxWidth(.7f)
                                .height(12.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                        )
                        Box(
                            Modifier.width(90.dp)
                                .height(8.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                        )
                    }
                }
            }
        }
    }
}
