package com.tomato.shell

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tomato.shell.engine.EngineManager
import com.tomato.shell.ui.AppViewModel
import com.tomato.shell.ui.screens.DetailScreen
import com.tomato.shell.ui.screens.DownloadScreen
import com.tomato.shell.ui.screens.SearchScreen
import com.tomato.shell.ui.screens.SettingsScreen
import com.tomato.shell.ui.theme.AccentButton
import com.tomato.shell.ui.theme.AuroraBackground
import com.tomato.shell.ui.theme.GlassButton
import com.tomato.shell.ui.theme.GlassPanel
import com.tomato.shell.ui.theme.GlassShape
import com.tomato.shell.ui.theme.LocalGlassColors
import com.tomato.shell.ui.theme.TomatoShellTheme

class MainActivity : ComponentActivity() {

    /** 外部分享/粘贴进来的文本（状态形式，Compose 侧消费后清空） */
    private val sharedText = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedText.value = extractSharedText(intent)
        setContent {
            TomatoShellTheme {
                App(sharedText = sharedText)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask：已在栈顶时再收一次分享
        sharedText.value = extractSharedText(intent)
    }

    private fun extractSharedText(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

    override fun onDestroy() {
        super.onDestroy()
        // 退出时停止引擎进程
        EngineManager.stop()
    }
}

@Composable
private fun App(sharedText: androidx.compose.runtime.MutableState<String?>) {
    val vm: AppViewModel = viewModel()
    val engineReady by vm.engineReady.collectAsState()
    val engineStarting by vm.engineStarting.collectAsState()
    val engineVersion by vm.engineVersion.collectAsState()
    val openBookId by vm.openBookId.collectAsState()
    val detailOwned by vm.detailOwned.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }

    // 详情页拦截系统返回键：返回上一个 tab 而不是退出 APP
    androidx.activity.compose.BackHandler(enabled = openBookId != null) {
        vm.closeDetail()
    }

    // 消费外部分享的文本（分享到本 APP 的番茄链接/ID → 直接进详情页）
    LaunchedEffect(sharedText.value) {
        sharedText.value?.let {
            vm.onExternalText(it)
            sharedText.value = null
        }
    }

    var showEngineSheet by rememberSaveable { mutableStateOf(false) }

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
                owned = detailOwned,
                onBack = { vm.closeDetail() },
            )
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Box(Modifier.weight(1f)) {
                    when (selectedTab) {
                        // 主页：搜索（未就绪时页内提示启动进度）
                        0 -> SearchScreen(
                            vm = vm,
                            onBookClick = { vm.openDetail(it) },
                            engineReady = engineReady,
                            engineStarting = engineStarting,
                        )
                        // 下载列表读本地磁盘，引擎未就绪也照常可用
                        1 -> DownloadScreen(vm = vm)
                        // 引擎状态大卡 + 自升级入口
                        2 -> SettingsScreen(
                            vm = vm,
                            onStatusClick = { showEngineSheet = true },
                            engineReady = engineReady,
                            engineStarting = engineStarting,
                            engineVersion = engineVersion,
                            appVersion = appVersion,
                        )
                    }
                }
                // 通栏纸底栏：键盘弹出时收起
                if (!imeVisible) {
                    GlassNavBar(selected = selectedTab, onSelect = { selectedTab = it })
                }
            }
        }
    }

    // 升级浮层必须声明在 AuroraBackground 之后，否则会被不透明背景盖住
    if (showEngineSheet) {
        EngineUpgradeSheet(vm = vm, currentVersion = engineVersion, onDismiss = {
            vm.resetEngineUpdatePanel()
            showEngineSheet = false
        })
    }
}

/** 统一页标题：所有页面同字号（28sp Bold）同边距 */
@Composable
internal fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 10.dp),
    )
}

/**
 * 引擎状态大卡（Clash Meta「运行中」卡式样）。
 * 就绪=主色底白字；启动中/失败=灰底。点卡片打开引擎升级浮层。放在「设置」页。
 */
@Composable
internal fun StatusCard(ready: Boolean, starting: Boolean, version: String, onStatusClick: () -> Unit) {
    val g = LocalGlassColors.current
    val cardColor = when {
        ready -> g.accentTop
        else -> Color(0xFF8E9196)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShape.card)
            .background(cardColor)
            .clickable(onClick = onStatusClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
            when {
                starting -> CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    strokeWidth = 3.dp,
                    color = Color.White,
                )
                ready -> Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
                else -> Icon(
                    Icons.Default.Cancel,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
            Column {
                Text(
                    text = when {
                        ready -> "引擎就绪"
                        starting -> "正在启动下载引擎"
                        else -> "引擎启动失败"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when {
                        ready -> "v${version.ifBlank { "?" }} · 点按检查引擎升级"
                        starting -> "首次启动需准备引擎文件，请稍候"
                        else -> "点按查看升级与排查选项"
                    },
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                )
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

/** 通栏纸底栏：细顶线分隔，选中项番茄红 + 短下划线 */
@Composable
private fun GlassNavBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline)
        )
        Row(Modifier.navigationBarsPadding()) {
            NavItem("搜索", Icons.Default.Search, selected == 0) { onSelect(0) }
            NavItem("下载", Icons.Default.Download, selected == 1) { onSelect(1) }
            NavItem("设置", Icons.Default.Settings, selected == 2) { onSelect(2) }
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
            .clickable(onClick = onClick)
            // 下划线用 drawBehind 画，不占布局空间——保证选中和未选中项等高
            .drawBehind {
                if (selected) {
                    val w = 20.dp.toPx()
                    val h = 3.dp.toPx()
                    drawRoundRect(
                        color = g.accentBottom,
                        topLeft = Offset((size.width - w) / 2f, 0f),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(h / 2f),
                    )
                }
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/**
 * 引擎升级浮层：检查上游新版本 → 下载并替换 → 重启引擎。
 * 引擎自带的 /api/self_update 在 linker64 启动模式下不可用，升级由 APP 完成（见 EngineUpdater）。
 * 自绘底部浮层（M3 ModalBottomSheet 与动态内容重排配合有诡异行为，弃用）。
 */
@Composable
private fun EngineUpgradeSheet(vm: AppViewModel, currentVersion: String, onDismiss: () -> Unit) {
    val state by vm.engineUpdate.collectAsState()
    val g = LocalGlassColors.current

    Box(Modifier.fillMaxSize()) {
        // 半透明遮罩，点空白处关闭
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(onClick = onDismiss)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 24.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                ) {
                    Text("引擎升级", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "当前版本 v${currentVersion.ifBlank { "?" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))

                    val info = state.info
                    when {
                        state.checking -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = g.accentBottom,
                            )
                            Text("正在检查新版本…", style = MaterialTheme.typography.bodyMedium)
                        }

                        info?.error != null -> Column {
                            Text(
                                text = info.error ?: "检查失败",
                                style = MaterialTheme.typography.bodyMedium,
                                color = g.danger,
                            )
                            Spacer(Modifier.height(12.dp))
                            GlassButton(text = "重试", onClick = { vm.checkEngineUpdate() })
                        }

                        info != null -> {
                            if (info.hasUpdate) {
                                Text(
                                    text = "发现新版本 ${info.latestTag}",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "升级会重启下载引擎，已下载的书不受影响",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    text = "已是最新版本（v${info.current}）",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Spacer(Modifier.height(14.dp))

                            when {
                                state.downloading -> Column {
                                    Text(
                                        text = "下载新引擎… ${state.percent}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { state.percent / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(GlassShape.pill),
                                        color = g.accentBottom,
                                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    )
                                }

                                state.restarting -> Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = g.accentBottom,
                                    )
                                    Text("正在替换引擎并重启…", style = MaterialTheme.typography.bodyMedium)
                                }

                                state.done -> Text(
                                    text = "✓ 升级完成，引擎已重启",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = g.ok,
                                )

                                else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (info.hasUpdate) {
                                        AccentButton(
                                            text = "下载并升级",
                                            height = 42.dp,
                                            onClick = { vm.startEngineUpdate() },
                                        )
                                    }
                                    GlassButton(text = "重新检查", onClick = { vm.checkEngineUpdate() })
                                }
                            }
                        }

                        else -> Column {
                            Text(
                                text = "检查上游 GitHub Release 是否有新版本引擎。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            AccentButton(
                                text = "检查引擎更新",
                                height = 42.dp,
                                onClick = { vm.checkEngineUpdate() },
                            )
                        }
                    }

                    state.error?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = g.danger)
                    }
                }
            }
        }
    }
}

/** 引擎启动失败时搜索页的日志卡（便于不连电脑排查）。启动中的提示由状态大卡承担。 */
@Composable
internal fun EngineFailurePane(vm: AppViewModel) {
    val log by vm.engineLog.collectAsState()
    val g = LocalGlassColors.current
    Column(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
        Text(
            text = "引擎启动失败，日志：",
            style = MaterialTheme.typography.titleSmall,
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
