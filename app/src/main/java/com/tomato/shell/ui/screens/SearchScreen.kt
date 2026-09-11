package com.tomato.shell.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tomato.shell.data.SearchItem
import com.tomato.shell.engine.EngineManager
import com.tomato.shell.ui.AppViewModel
import com.tomato.shell.ui.theme.GlassPanel
import com.tomato.shell.ui.theme.GlassShape
import com.tomato.shell.ui.theme.LocalGlassColors

/**
 * 搜索页：输入关键词 → 展示搜索结果 → 点击进详情页。
 */
@Composable
fun SearchScreen(vm: AppViewModel, onBookClick: (String) -> Unit) {
    val query by vm.searchQuery.collectAsState()
    val results by vm.searchResults.collectAsState()
    val searching by vm.searching.collectAsState()
    val error by vm.searchError.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        GlassSearchField(
            query = query,
            onQueryChange = { vm.onQueryChange(it) },
            onSearch = { vm.search() },
            searching = searching,
        )

        // 热门搜索快捷入口：省去打字，也方便快速试用手感
        if (results.isEmpty() && query.isEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "热门搜索",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("斗破苍穹", "完美世界", "遮天").forEach { word ->
                    GlassPanel(
                        shape = GlassShape.pill,
                        shadowElevation = 0.dp,
                        onClick = {
                            vm.onQueryChange(word)
                            vm.search()
                        },
                    ) {
                        Text(
                            text = word,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

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

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                // 底部留出悬浮底栏的高度，最后一张卡能完整滚出来
                contentPadding = PaddingValues(bottom = 110.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(results, key = { it.bookId }) { item ->
                    BookCard(
                        item = item,
                        onClick = { onBookClick(item.bookId) },
                    )
                }
            }
        }
    }
}

/** 毛玻璃胶囊搜索框 + 番茄渐变圆形搜索按钮 */
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
                                    text = "输入书名或作者搜索",
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
        // 圆形渐变搜索按钮
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

@Composable
private fun BookCard(item: SearchItem, onClick: () -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(12.dp)) {
            // 封面可能是相对路径，拼上引擎 baseUrl
            val coverUrl = item.bestCover?.let {
                if (it.startsWith("http")) it else EngineManager.baseUrl + it
            }
            AsyncImage(
                model = coverUrl,
                contentDescription = item.displayName,
                modifier = Modifier
                    .size(72.dp, 96.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.align(Alignment.CenterVertically)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.author.ifBlank { "未知作者" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "查看详情 ›",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalGlassColors.current.accentBottom,
                )
            }
        }
    }
}
