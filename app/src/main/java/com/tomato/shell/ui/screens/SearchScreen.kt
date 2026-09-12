package com.tomato.shell.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tomato.shell.EngineFailurePane
import com.tomato.shell.PageTitle
import com.tomato.shell.data.SearchItem
import com.tomato.shell.engine.EngineManager
import com.tomato.shell.ui.AppViewModel
import com.tomato.shell.ui.theme.GlassPanel
import com.tomato.shell.ui.theme.GlassShape
import com.tomato.shell.ui.theme.LocalGlassColors

/**
 * 主页（搜索）：大标题 + 引擎状态大卡 + 搜索框 + 「继续读」横滑架 + 双列网格结果。
 * 对应设计稿 V2「书城货架」。
 */
@Composable
fun SearchScreen(
    vm: AppViewModel,
    onBookClick: (String) -> Unit,
    engineReady: Boolean,
    engineStarting: Boolean,
) {
    val query by vm.searchQuery.collectAsState()
    val results by vm.searchResults.collectAsState()
    val searching by vm.searching.collectAsState()
    val error by vm.searchError.collectAsState()
    val remoteChapters by vm.remoteChapters.collectAsState()
    val jobs by vm.jobs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        PageTitle("番茄下载")
        Spacer(Modifier.height(14.dp))

        if (!engineReady) {
            // 引擎未就绪：启动中给个轻提示（进度见「设置」页）；失败展示日志
            if (engineStarting) {
                GlassPanel(modifier = Modifier.fillMaxWidth(), shape = GlassShape.inner) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = LocalGlassColors.current.accentBottom,
                        )
                        Text(
                            text = "正在启动下载引擎…（进度见「设置」页）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                EngineFailurePane(vm)
            }
            return@Column
        }
        GlassSearchField(
            query = query,
            onQueryChange = { vm.onQueryChange(it) },
            onSearch = { vm.search() },
            searching = searching,
        )

        if (query.isNotBlank() && results.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "搜索结果", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "“$query”",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            searching -> Box(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = LocalGlassColors.current.accentBottom,
                )
            }

            error != null -> GlassPanel(modifier = Modifier.fillMaxWidth(), shape = GlassShape.inner) {
                Text(
                    text = error!!,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            results.isEmpty() && query.isNotEmpty() -> Text(
                text = "未找到结果",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(results, key = { it.bookId }) { item ->
                    // 该书在本地已有且远端章节更多 → 结果卡上标「有更新」
                    val updatable = jobs.any {
                        it.local && it.bookId == item.bookId &&
                            (remoteChapters[it.bookId] ?: 0L) > (it.localChapters ?: 0L)
                    }
                    BookCard(item = item, updatable = updatable, onClick = { onBookClick(item.bookId) })
                }
            }
        }
    }
}

/** 白卡 + 红色圆形搜索钮 */
@Composable
private fun GlassSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    searching: Boolean,
) {
    val g = LocalGlassColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlassPanel(modifier = Modifier.weight(1f), shape = GlassShape.pill, shadowElevation = 6.dp) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 15.sp,
                    ),
                    cursorBrush = SolidColor(g.accentBottom),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    text = "书名 / ID / 番茄链接",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(g.accentTop, g.accentBottom)))
                .clickable(enabled = !searching, onClick = onSearch),
            contentAlignment = Alignment.Center,
        ) {
            if (searching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** 网格书卡：封面顶图（3:4）+ 固定两行书名 + 作者/章节数（等高对齐） */
@Composable
private fun BookCard(item: SearchItem, updatable: Boolean, onClick: () -> Unit) {
    val g = LocalGlassColors.current
    GlassPanel(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            Box {
                val coverUrl = item.bestCover?.let {
                    if (it.startsWith("http")) it else EngineManager.baseUrl + it
                }
                AsyncImage(
                    model = coverUrl,
                    contentDescription = item.displayName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f),
                    contentScale = ContentScale.Crop,
                )
                if (updatable) {
                    CornerTag(text = "有更新", modifier = Modifier.align(Alignment.TopEnd))
                }
            }
            Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildAnnotatedString {
                        append(item.author.ifBlank { "未知作者" })
                        item.raw?.chapterNumber?.takeIf { it.isNotBlank() }?.let {
                            append(" · ${it}章")
                        }
                        if (updatable) {
                            withStyle(SpanStyle(color = g.accentBottom, fontWeight = FontWeight.Bold)) {
                                append(" · 有更新")
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 卡片角标：半透明黑底白字（有更新） */
@Composable
private fun CornerTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .padding(7.dp)
            .clip(GlassShape.pill)
            .background(Color(0x73000000))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
    )
}
