package com.tomato.shell.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFFC62828),          // 深番茄红（Clash Meta 式强调色）
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF5DBD8),
    onPrimaryContainer = Color(0xFF5C1A0A),
    secondary = Color(0xFF2F6B4F),        // 绿（成功类）
    onSecondary = Color.White,
    background = Color(0xFFF2F3F5),       // 浅灰底
    onBackground = Color(0xFF1C1B1F),     // 近黑
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1714),
    surfaceVariant = Color(0xFFE8E9EB),
    onSurfaceVariant = Color(0xFF5F5F5F), // 次级灰
    error = Color(0xFFB3261E),
    outline = Color(0xFFE3E4E6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE05545),
    onPrimary = Color(0xFF200A04),
    primaryContainer = Color(0xFF5C2413),
    onPrimaryContainer = Color(0xFFFFD9CD),
    secondary = Color(0xFF5B9A78),
    onSecondary = Color(0xFF00220F),
    background = Color(0xFF141619),       // 深灰黑
    onBackground = Color(0xFFECEDEF),
    surface = Color(0xFF1E2126),
    onSurface = Color(0xFFECEDEF),
    surfaceVariant = Color(0xFF262A30),
    onSurfaceVariant = Color(0xFF9AA0A8),
    error = Color(0xFFE5484D),
    outline = Color(0xFF2E3238),
)

/** 苹果风字阶：系统无衬线字体 + 明确的重量层级（标题加粗、正文常规、标签中黑） */
private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp),
)

@Composable
fun TomatoShellTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = scheme, typography = AppTypography) {
        // 默认文字颜色跟随主题（没有 Surface 包裹时 Material3 不会自动提供）
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground) {
            CompositionLocalProvider(
                LocalGlassColors provides if (darkTheme) darkGlass() else lightGlass(),
                content = content,
            )
        }
    }
}
