package com.tomato.shell.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 「纸页·书卷」设计系统。
 *
 * 理念：小说 App 的本质是书——米白纸底 + 墨色文字 + 番茄红点睛，
 * 白纸卡片 + 细描边分层，无玻璃、无渐变、无彩投影，安静耐看。
 * 组件名保留 Glass* 前缀（历史沿用），视觉已全部换为纸页语言。
 */
data class GlassColors(
    val base: Color,          // 页面底色
    val panelTop: Color,      // 卡片填充（纯白）
    val accentTop: Color,     // 主色（按钮/选中态）
    val accentBottom: Color,
    val ok: Color,            // 叶绿：成功/就绪
    val warn: Color,          // 赭石：进行中
    val danger: Color,        // 砖红：失败
    val info: Color,          // 灰蓝：信息徽标
)

fun lightGlass() = GlassColors(
    base = Color(0xFFF2F3F5),          // 浅灰底
    panelTop = Color(0xFFFFFFFF),      // 白卡
    accentTop = Color(0xFF4D9DF8),      // 浅蓝
    accentBottom = Color(0xFF4D9DF8),
    ok = Color(0xFF2F6B4F),            // 绿：成功/就绪
    warn = Color(0xFFB45309),          // 赭石：进行中
    danger = Color(0xFFB3261E),        // 砖红：失败
    info = Color(0xFF3D6B8B),          // 灰蓝：信息徽标
)

fun darkGlass() = GlassColors(
    base = Color(0xFF141619),          // 深灰黑
    panelTop = Color(0xFF1E2126),      // 深色卡
    accentTop = Color(0xFF7AB8FF),
    accentBottom = Color(0xFF7AB8FF),
    ok = Color(0xFF5B9A78),
    warn = Color(0xFFD9922B),
    danger = Color(0xFFE5484D),
    info = Color(0xFF6FA3C4),
)

val LocalGlassColors = staticCompositionLocalOf { lightGlass() }

/** 圆角令牌：卡片 / 内嵌块 / 胶囊 */
object GlassShape {
    val card = RoundedCornerShape(14.dp)
    val inner = RoundedCornerShape(12.dp)
    val pill = RoundedCornerShape(999.dp)
}

/** 页面背景：纯色底 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val g = LocalGlassColors.current
    Box(modifier.fillMaxSize().background(g.base), content = content)
}

/**
 * 标准卡片：纯白填充 + 极浅阴影，无描边（Clash Meta 式卡片语言）。
 * 传 onClick 即为可点击卡片（涟漪被 clip 在圆角内）。
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape.card,
    shadowElevation: Dp = 2.dp,   // 极浅阴影
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val g = LocalGlassColors.current
    var m = modifier
        .shadow(shadowElevation, shape, clip = false)
        .clip(shape)
        .background(g.panelTop)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m, content = content)
}

/** 番茄红实心胶囊主按钮 */
@Composable
fun AccentButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    horizontalPadding: Dp = 20.dp,
    onClick: () -> Unit,
) {
    val g = LocalGlassColors.current
    Box(
        modifier = modifier
            .height(height)
            .clip(GlassShape.pill)
            .alpha(if (enabled) 1f else 0.45f)
            .background(Brush.linearGradient(listOf(g.accentTop, g.accentBottom)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = horizontalPadding),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 灰底胶囊次级按钮（tonal 风格） */
@Composable
fun GlassButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(GlassShape.pill)
            .alpha(if (enabled) 1f else 0.5f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** 彩色小徽标：淡色胶囊底 + 同色文字 */
@Composable
fun PillBadge(text: String, tint: Color) {
    Box(
        Modifier
            .clip(GlassShape.pill)
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
