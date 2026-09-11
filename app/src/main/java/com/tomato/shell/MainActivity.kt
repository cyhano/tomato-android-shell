package com.tomato.shell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tomato.shell.engine.EngineManager
import com.tomato.shell.ui.AppViewModel
import com.tomato.shell.ui.screens.DetailScreen
import com.tomato.shell.ui.screens.DownloadScreen
import com.tomato.shell.ui.screens.SearchScreen
import com.tomato.shell.ui.theme.AuroraBackground
import com.tomato.shell.ui.theme.GlassPanel
import com.tomato.shell.ui.theme.GlassShape
import com.tomato.shell.ui.theme.LocalGlassColors
import com.tomato.shell.ui.theme.TomatoShellTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TomatoShellTheme {
                App()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 退出时停止引擎进程
        EngineManager.stop()
    }
}

@Composable
private fun App() {
    val vm: AppViewModel = viewModel()
    val engineReady by vm.engineReady.collectAsState()
    val engineStarting by vm.engineStarting.collectAsState()
    val engineVersion by vm.engineVersion.collectAsState()
    val openBookId by vm.openBookId.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // 键盘弹出时把底栏收起来，输入不被遮挡
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    AuroraBackground(Modifier.fillMaxSize()) {
        if (openBookId != null) {
            // 详情页：整页覆盖（自己处理状态栏边距）
            val detail by vm.detail.collectAsState()
            val detailLoading by vm.detailLoading.collectAsState()
            val rangeText by vm.rangeText.collectAsState()
            val creating by vm.creatingJob.collectAsState()
            val detailError by vm.detailError.collectAsState()
            DetailScreen(
                detail = detail,
                loading = detailLoading,
                rangeText = rangeText,
                onRangeChange = { vm.onRangeChange(it) },
                onDownload = {
                    vm.downloadFromDetail()
                    selectedTab = 1
                },
                downloading = creating,
                error = detailError,
                onBack = { vm.closeDetail() },
            )
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                EngineTopBar(
                    ready = engineReady,
                    starting = engineStarting,
                    version = engineVersion,
                )
                Box(Modifier.weight(1f)) {
                    when (selectedTab) {
                        // 搜索依赖引擎 API：未就绪时在页内提示启动进度
                        0 -> if (engineReady) {
                            SearchScreen(vm = vm, onBookClick = { vm.openDetail(it) })
                        } else {
                            EngineStartingPane(vm = vm)
                        }
                        // 下载列表读本地磁盘，引擎未就绪也照常可用
                        1 -> DownloadScreen(vm = vm)
                    }
                }
            }
            // 悬浮毛玻璃底栏：内容从它下面滚过
            AnimatedVisibility(
                visible = !imeVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                GlassNavBar(
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                    modifier = Modifier.navigationBarsPadding().padding(bottom = 10.dp),
                )
            }
        }
    }
}

/** 顶栏：应用名 + 引擎状态胶囊（就绪绿点 / 启动中脉冲橙点 / 失败红点） */
@Composable
private fun EngineTopBar(ready: Boolean, starting: Boolean, version: String) {
    val g = LocalGlassColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "番茄下载",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        GlassPanel(shape = GlassShape.pill, shadowElevation = 0.dp) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val dotColor = when {
                    ready -> g.ok
                    starting -> g.warn
                    else -> g.danger
                }
                if (starting) PulsingDot(dotColor) else StatusDot(dotColor)
                Text(
                    text = when {
                        ready -> if (version.isBlank()) "引擎就绪" else "引擎就绪 $version"
                        starting -> "引擎启动中"
                        else -> "引擎失败"
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
}

/** 启动中的呼吸圆点 */
@Composable
private fun PulsingDot(color: Color) {
    val t = rememberInfiniteTransition(label = "pulse")
    val alpha by t.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(Modifier.size(7.dp).clip(CircleShape).background(color).alpha(alpha))
}

/** 悬浮玻璃胶囊底栏 */
@Composable
private fun GlassNavBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(
        modifier = modifier
            .padding(horizontal = 48.dp)
            .fillMaxWidth(),
        shape = GlassShape.pill,
        shadowElevation = 16.dp,
    ) {
        Row(Modifier.padding(6.dp)) {
            NavItem("搜索", Icons.Default.Search, selected == 0) { onSelect(0) }
            NavItem("下载", Icons.Default.Download, selected == 1) { onSelect(1) }
        }
    }
}

@Composable
private fun RowScope.NavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val g = LocalGlassColors.current
    val tint = if (selected) g.accentBottom else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(GlassShape.pill)
            .background(if (selected) tint.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/**
 * 引擎启动中/失败时搜索页的占位内容。
 * 启动中显示玻璃卡片加载态；失败显示引擎日志便于排查。
 */
@Composable
private fun EngineStartingPane(vm: AppViewModel) {
    val starting by vm.engineStarting.collectAsState()
    val log by vm.engineLog.collectAsState()
    val g = LocalGlassColors.current

    if (starting) {
        // 冷启动要复制 9MB 引擎并等它监听端口，约 10-20 秒
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlassPanel(shadowElevation = 18.dp) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(
                        color = g.accentBottom,
                        modifier = Modifier.size(34.dp),
                        strokeWidth = 3.dp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(text = "正在启动下载引擎…", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "首次启动需准备引擎文件，约需十几秒\n启动后搜索即可用；下载列表不受影响",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    } else {
        // 启动失败：显示引擎日志，便于不连电脑也能排查
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text(
                text = "引擎启动失败",
                style = MaterialTheme.typography.titleMedium,
                color = g.danger,
            )
            Spacer(Modifier.height(10.dp))
            GlassPanel(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.ifBlank { "(无日志)" },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                )
            }
        }
    }
}
