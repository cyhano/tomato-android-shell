package com.tomato.shell.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
 * 毛玻璃设计系统（苹果风）。
 *
 * 思路：Compose 没有开箱的 backdrop blur，用「极光色斑 + 半透明玻璃面板」来等效——
 * 底层铺几团径向渐变的柔光色块（中心实色向边缘淡出，视觉上就是高斯模糊过的光斑，
 * 且不依赖 RenderEffect，API 26 也能出效果），上层玻璃卡片用半透明渐变填充 +
 * 发丝描边 + 柔影，透出底下的颜色即成「毛玻璃」。
 */
data class GlassColors(
    val base: Color,          // 背景底色
    val blobs: List<Color>,   // 极光色斑颜色
    val panelTop: Color,      // 玻璃面板渐变顶部
    val panelBottom: Color,   // 玻璃面板渐变底部
    val panelBorder: Color,   // 玻璃面板描边
    val shadow: Color,        // 卡片柔影
    val accentTop: Color,     // 主按钮渐变（番茄红）
    val accentBottom: Color,
    val ok: Color,            // 就绪/成功
    val warn: Color,          // 进行中
    val danger: Color,        // 失败
    val info: Color,          // 信息蓝（本地徽标等）
)

fun lightGlass() = GlassColors(
    base = Color(0xFFEEF1F7),
    blobs = listOf(
        Color(0x73FF5A3C), // 番茄橙
        Color(0x594DA3FF), // 天蓝
        Color(0x4C9B6BFF), // 紫罗兰
        Color(0x4034D1A6), // 薄荷绿
    ),
    panelTop = Color(0xB8FFFFFF),
    panelBottom = Color(0x66FFFFFF),
    panelBorder = Color(0x99FFFFFF),
    shadow = Color(0x1A101828),
    accentTop = Color(0xFFFF7A3D),
    accentBottom = Color(0xFFFF3B2F),
    ok = Color(0xFF34C759),
    warn = Color(0xFFFF9F0A),
    danger = Color(0xFFE5484D),
    info = Color(0xFF3D8BFF),
)

fun darkGlass() = GlassColors(
    base = Color(0xFF07090F),
    blobs = listOf(
        Color(0x38FF5A3C),
        Color(0x402E5BFF),
        Color(0x337A3CFF),
        Color(0x2620A88A),
    ),
    panelTop = Color(0x17FFFFFF),
    panelBottom = Color(0x09FFFFFF),
    panelBorder = Color(0x24FFFFFF),
    shadow = Color(0x66000000),
    accentTop = Color(0xFFFF8A55),
    accentBottom = Color(0xFFFF4D36),
    ok = Color(0xFF30D158),
    warn = Color(0xFFFFB340),
    danger = Color(0xFFFF6361),
    info = Color(0xFF64B5FF),
)

val LocalGlassColors = staticCompositionLocalOf { lightGlass() }

/** 圆角令牌：卡片 / 内嵌块 / 胶囊 */
object GlassShape {
    val card = RoundedCornerShape(22.dp)
    val inner = RoundedCornerShape(14.dp)
    val pill = RoundedCornerShape(999.dp)
}

/** 极光背景：底色 + 四团柔光色斑，内容绘制在最上层 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val g = LocalGlassColors.current
    Box(modifier.fillMaxSize().background(g.base)) {
        Blob(g.blobs[0], 340.dp, Alignment.TopEnd, 70.dp, (-90).dp)
        Blob(g.blobs[1], 420.dp, Alignment.CenterStart, (-150).dp, (-60).dp)
        Blob(g.blobs[2], 380.dp, Alignment.BottomEnd, 80.dp, 150.dp)
        Blob(g.blobs[3], 300.dp, Alignment.BottomStart, (-100).dp, 180.dp)
        content()
    }
}

/** 单团柔光：径向渐变从实色淡出到全透明（等价于被高斯模糊过的色块） */
@Composable
private fun BoxScope.Blob(color: Color, size: Dp, align: Alignment, offsetX: Dp, offsetY: Dp) {
    Box(
        Modifier
            .align(align)
            .offset(x = offsetX, y = offsetY)
            .size(size)
            .background(Brush.radialGradient(listOf(color, color.copy(alpha = 0f))))
    )
}

/**
 * 毛玻璃面板：半透明渐变填充 + 发丝描边 + 柔影。
 * 传 onClick 即为可点击卡片（涟漪被 clip 在圆角内）。
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape.card,
    shadowElevation: Dp = 10.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val g = LocalGlassColors.current
    var m = modifier
        .shadow(shadowElevation, shape, clip = false, ambientColor = g.shadow, spotColor = g.shadow)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(g.panelTop, g.panelBottom)))
        .border(1.dp, g.panelBorder, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m, content = content)
}

/** 番茄渐变主按钮（胶囊形） */
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

/** 玻璃次级按钮（胶囊形，无渐变） */
@Composable
fun GlassButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    GlassPanel(
        modifier = modifier,
        shape = GlassShape.pill,
        shadowElevation = 0.dp,
        onClick = if (enabled) onClick else null,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 彩色小徽标：淡色胶囊底 + 同色文字 */
@Composable
fun PillBadge(text: String, tint: Color) {
    Box(
        Modifier
            .clip(GlassShape.pill)
            .background(tint.copy(alpha = 0.16f))
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
