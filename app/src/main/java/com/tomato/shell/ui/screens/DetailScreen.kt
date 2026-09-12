package com.tomato.shell.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tomato.shell.data.BookDetail
import com.tomato.shell.engine.EngineManager
import com.tomato.shell.ui.theme.AccentButton
import com.tomato.shell.ui.theme.GlassPanel
import com.tomato.shell.ui.theme.GlassShape
import com.tomato.shell.ui.theme.LocalGlassColors
import com.tomato.shell.ui.theme.PillBadge

/**
 * 书籍详情页：展示书籍信息，并让用户选择要下载的章节范围。
 *
 * 章节范围输入规则（对应引擎 range_start / range_end）：
 * - 空           → 全部下载
 * - `1-100`      → 下载第 1 到 100 章
 * - `50`         → 从第 50 章开始，一直到最后
 */
@Composable
fun DetailScreen(
    detail: BookDetail?,
    loading: Boolean,
    rangeText: String,
    onRangeChange: (String) -> Unit,
    onDownload: () -> Unit,
    downloading: Boolean,
    error: String?,
    onBack: () -> Unit,
) {
    val scroll = rememberScrollState()
    val keyboard = LocalSoftwareKeyboardController.current
    val g = LocalGlassColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scroll)
            // 键盘弹出时把内容顶上去，避免「下载」按钮被输入法挡住
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        // 返回
        Row(
            modifier = Modifier
                .clip(GlassShape.pill)
                .clickable(onClick = onBack)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.titleLarge,
                color = g.accentBottom,
            )
            Text(
                text = "返回搜索",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = g.accentBottom,
            )
        }

        if (loading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = g.accentBottom)
            }
            return@Column
        }

        if (detail == null) {
            Spacer(Modifier.height(20.dp))
            GlassPanel(modifier = Modifier.fillMaxWidth(), shape = GlassShape.inner) {
                Text(
                    text = error ?: "加载详情失败",
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            return@Column
        }

        Spacer(Modifier.height(14.dp))

        // 书籍信息卡（封面 + 基本信息）
        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(15.dp)) {
            val cover = detail.coverUrl?.let {
                if (it.startsWith("http")) it else EngineManager.baseUrl + it
            }
            AsyncImage(
                model = cover,
                contentDescription = detail.bookName,
                modifier = Modifier
                    .size(110.dp, 150.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.align(Alignment.Top)) {
                Text(
                    text = detail.bookName,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "作者：${detail.author ?: "未知"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                detail.category?.let {
                    Spacer(Modifier.height(8.dp))
                    PillBadge(it, g.info)
                }
                Spacer(Modifier.height(8.dp))
                val bits = buildList {
                    detail.chapterCount?.let { add("${it} 章") }
                    detail.wordCount?.let { add("${it / 10000} 万字") }
                    detail.score?.takeIf { it > 0 }?.let { add("评分 $it") }
                }
                if (bits.isNotEmpty()) {
                    Text(
                        text = bits.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                detail.readCountText?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 下载范围卡：输入框与「下载」按钮同一行，避免键盘弹出时按钮被挡
        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(15.dp)) {
            Text(
                text = "下载范围",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            GlassPanel(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 6.dp,
            ) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
                    if (rangeText.isEmpty()) {
                        Text(
                            text = "留空=全部；1-100 或 50",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = rangeText,
                        onValueChange = onRangeChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 15.sp,
                        ),
                        cursorBrush = SolidColor(g.accentBottom),
                        // 键盘右下角显示「完成」，按一下即收起键盘
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                    )
                }
            }
                AccentButton(
                    text = if (downloading) "创建中…" else "下载",
                    enabled = !downloading,
                    height = 48.dp,
                    horizontalPadding = 22.dp,
                    onClick = onDownload,
                )
            }
            Spacer(Modifier.height(6.dp))
            val total = detail.chapterCount ?: 0
            Text(
                text = "共 $total 章。" + (parseRangeHint(rangeText, total) ?: "留空则全部下载"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "已下载过的章节会自动跳过，不会重复下载",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }
        }

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(12.dp))

        // 简介
        val desc = detail.description
        if (!desc.isNullOrBlank()) {
            GlassPanel(modifier = Modifier.fillMaxWidth(), shape = GlassShape.inner) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "简介",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

/**
 * 解析用户输入的范围，返回给用户看的提示文案。
 * 空 → null（调用方显示"留空则全部下载"）
 * `1-100` → "将下载第 1-100 章"
 * `50` → "将从第 50 章下载到最后一章"
 * 非法 → "格式不对..."
 */
private fun parseRangeHint(input: String, total: Long): String? {
    val s = input.trim()
    if (s.isEmpty()) return null
    return when (val r = ChapterRangeParser.parse(s, total)) {
        is ChapterRangeParser.Result.All -> "将下载全部章节"
        is ChapterRangeParser.Result.Range -> "将下载第 ${r.start}-${r.end} 章"
        is ChapterRangeParser.Result.Invalid -> "⚠ ${r.reason}"
    }
}

/**
 * 章节范围解析（纯逻辑，便于单独测试）。
 *
 * 支持：""（全部）、"50"（从50到末尾）、"1-100"、"1 - 100"
 */
object ChapterRangeParser {

    sealed interface Result {
        /** 不限制，下载全部 */
        object All : Result
        /** 指定区间（均已做边界收敛，1-based，含两端） */
        data class Range(val start: Int, val end: Int) : Result
        data class Invalid(val reason: String) : Result
    }

    fun parse(input: String, totalChapters: Long): Result {
        val s = input.trim()
        if (s.isEmpty()) return Result.All

        // 单数字：从该章起，到最后一章
        if (s.toIntOrNull() != null) {
            val n = s.toInt()
            if (n < 1) return Result.Invalid("章节号需 ≥ 1")
            if (totalChapters > 0 && n > totalChapters) {
                return Result.Invalid("超出总章节数（共 $totalChapters 章）")
            }
            val end = if (totalChapters > 0) totalChapters.toInt() else n
            return Result.Range(n, end)
        }

        // 区间：start-end
        val parts = s.split("-").map { it.trim() }
        if (parts.size == 2) {
            val a = parts[0].toIntOrNull()
            val b = parts[1].toIntOrNull()
            if (a == null || b == null) return Result.Invalid("格式应为 1-100")
            if (a < 1 || b < 1) return Result.Invalid("章节号需 ≥ 1")
            if (a > b) return Result.Invalid("起始章不能大于结束章")
            if (totalChapters > 0 && b > totalChapters) {
                return Result.Invalid("超出总章节数（共 $totalChapters 章）")
            }
            return Result.Range(a, b)
        }

        return Result.Invalid("格式应为 1-100 或 50")
    }
}
