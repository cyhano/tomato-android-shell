package com.tomato.shell.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tomato.shell.data.Job
import com.tomato.shell.ui.AppViewModel
import com.tomato.shell.ui.theme.AccentButton
import com.tomato.shell.ui.theme.GlassButton
import com.tomato.shell.ui.theme.GlassPanel
import com.tomato.shell.ui.theme.GlassShape
import com.tomato.shell.ui.theme.LocalGlassColors
import com.tomato.shell.ui.theme.PillBadge

/**
 * 下载页：任务列表 + 进度 + 「检查更新」。
 */
@Composable
fun DownloadScreen(vm: AppViewModel) {
    val jobs by vm.jobs.collectAsState()
    val scanning by vm.updateScanning.collectAsState()
    val updateResult by vm.updateResult.collectAsState()
    val openMessage by vm.openMessage.collectAsState()
    val remoteChapters by vm.remoteChapters.collectAsState()
    val g = LocalGlassColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        // 标题行（检查更新移到右下角 FAB）
        Text(
            text = "下载任务",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        // 更新扫描结果
        updateResult?.let {
            Spacer(Modifier.height(10.dp))
            GlassPanel(modifier = Modifier.fillMaxWidth(), shape = GlassShape.inner) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.clearUpdateResult() }) {
                        Text("知道了", color = g.accentBottom)
                    }
                }
            }
        }

        // 打开文件的提示
        openMessage?.let {
            Spacer(Modifier.height(10.dp))
            GlassPanel(modifier = Modifier.fillMaxWidth(), shape = GlassShape.inner) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.clearOpenMessage() }) {
                        Text("知道了", color = g.accentBottom)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(Modifier.fillMaxSize()) {
            if (jobs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlassPanel {
                        Column(
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "还没有下载任务",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "到「搜索」页找一本书\n选择章节后开始下载",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    // 底部给 FAB 留出空间，最后一张卡能完整滚出来
                    contentPadding = PaddingValues(bottom = 84.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(jobs, key = { it.id }) { job ->
                        JobCard(
                            job = job,
                            // 与检查更新同一判定：远端章节数 > 本地实际拥有 → 显示「更新」
                            canUpdate = job.local && job.bookId != null &&
                                (remoteChapters[job.bookId] ?: 0L) > (job.localChapters ?: 0L),
                            onUpdate = { vm.updateBook(job) },
                            onCancel = { vm.cancelJob(job.id) },
                            onOpen = { vm.openDownloadedBook(job) },
                        )
                    }
                }
            }

            // 右下角 FAB：检查更新（Clash Meta 代理页延迟测试钮式样）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 4.dp)
                    .size(56.dp)
                    .shadow(4.dp, CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(g.accentTop)
                    .clickable(enabled = !scanning) { vm.checkUpdates() },
                contentAlignment = Alignment.Center,
            ) {
                if (scanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = Color.White,
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "检查更新",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun JobCard(
    job: Job,
    canUpdate: Boolean = false,
    onUpdate: () -> Unit = {},
    onCancel: () -> Unit = {},
    onOpen: () -> Unit,
) {
    val done = job.state == "done"
    val isLocal = job.local
    val g = LocalGlassColors.current
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (done) onOpen() },
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (isLocal) {
                // 本地已有书：封面缩略图 + 标题 + 元信息（设计稿 V2 下载卡）
                LocalBookBody(job = job, canUpdate = canUpdate, onUpdate = onUpdate, onOpen = onOpen)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = job.title ?: job.bookId ?: "任务#${job.id}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(state = job.state)
            }
            Spacer(Modifier.height(10.dp))


            val progress = job.progress
            val total = progress?.chapterTotal ?: 0
            val saved = progress?.savedChapters ?: 0
            // 指定范围下载时，引擎的 total 就是范围内章数，进度按它算即可
            val fraction = if (total > 0) (saved.toFloat() / total).coerceIn(0f, 1f) else 0f

            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(GlassShape.pill),
                color = g.accentBottom,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.height(8.dp))

            val phase = progress?.savePhase ?: ""
            val progressText = buildAnnotatedString {
                if (phase.isNotEmpty()) append("$phase  ")
                if (total > 0) {
                    withStyle(SpanStyle(color = g.accentBottom, fontWeight = FontWeight.Bold)) {
                        append("$saved")
                    }
                    append(" / $total 章")
                } else if (job.state == "running") {
                    append("下载中…")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                when {
                    done -> AccentButton(
                        text = "打开",
                        height = 34.dp,
                        horizontalPadding = 16.dp,
                        onClick = onOpen,
                    )

                    job.state == "running" || job.state == "queued" -> GlassButton(
                        text = "取消",
                        onClick = onCancel,
                    )
                }
            }

            if (done) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "点卡片或「打开」用其它阅读 App 打开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            job.message?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * 本地已有书的 V2 下载卡：封面缩略图 + 标题 + 元信息（已下载章节数红色高亮）
 * + 底部「更新」（tonal）与「打开」（实心主色）按钮。
 * 元信息来自 status.json（App 启动时扫描，字段可能缺省）。
 */
@Composable
private fun LocalBookBody(job: Job, canUpdate: Boolean, onUpdate: () -> Unit, onOpen: () -> Unit) {
    val g = LocalGlassColors.current
    Column {
        Row {
            // 封面（本地文件，Coil 直接加载 File）
            val cover = job.coverPath?.let { java.io.File(it) }
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = job.title,
                    modifier = Modifier
                        .size(width = 52.dp, height = 70.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.title ?: job.bookId ?: "任务#${job.id}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // 作者 / 分类
                val meta1 = listOfNotNull(
                    job.author?.takeIf { it.isNotBlank() }?.let { it },
                    job.category?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (meta1.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = meta1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 已下载 N 章（数字红色高亮）/ 字数 / 文件大小
                val owned = job.localChapters ?: 0L
                val meta2 = buildAnnotatedString {
                    var sep = ""
                    if (owned > 0) {
                        append("已下载 ")
                        withStyle(SpanStyle(color = g.accentBottom, fontWeight = FontWeight.Bold)) {
                            append("$owned")
                        }
                        append(" 章")
                        sep = " · "
                    }
                    job.wordCount?.takeIf { it > 0 }?.let {
                        append(sep); append("${it / 10000} 万字"); sep = " · "
                    }
                    job.localSize?.takeIf { it > 0 }?.let {
                        append(sep); append(formatSize(it))
                    }
                }
                if (meta2.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = meta2,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                // 最新章节（灰）
                job.lastChapterTitle?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "最新：$it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 简介（最多 2 行）
                job.description?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "App 重启后仍可打开",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // 「更新」在「打开」左边：增量续传新章节，完成后自动合并进同一个 EPUB
            if (canUpdate) {
                GlassButton(text = "更新", onClick = onUpdate)
                Spacer(Modifier.width(8.dp))
            }
            AccentButton(
                text = "打开",
                height = 34.dp,
                horizontalPadding = 16.dp,
                onClick = onOpen,
            )
        }
    }
}

/** 状态徽标 */
@Composable
private fun StatusBadge(state: String?) {
    val g = LocalGlassColors.current
    val (text, color) = when (state) {
        "queued" -> "排队中" to g.warn
        "running" -> "下载中" to g.accentBottom
        "done" -> "已完成" to g.ok
        "error" -> "失败" to g.danger
        "cancelled" -> "已取消" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> (state ?: "未知") to MaterialTheme.colorScheme.onSurfaceVariant
    }
    PillBadge(text, color)
}

/** 字节数格式化 */
private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
