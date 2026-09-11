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
    primary = Color(0xFFFF4B26),          // 番茄橙红
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCD2),
    onPrimaryContainer = Color(0xFF5C1A0A),
    secondary = Color(0xFF3D8BFF),
    onSecondary = Color.White,
    background = Color(0xFFEEF1F7),
    onBackground = Color(0xFF171A21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171A21),
    surfaceVariant = Color(0xFFE2E6EF),
    onSurfaceVariant = Color(0xFF5C6575),
    error = Color(0xFFE5484D),
    outline = Color(0xFFC6CCD8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF6B45),
    onPrimary = Color(0xFF200A04),
    primaryContainer = Color(0xFF5C2413),
    onPrimaryContainer = Color(0xFFFFD9CD),
    secondary = Color(0xFF64B5FF),
    onSecondary = Color(0xFF00223F),
    background = Color(0xFF07090F),
    onBackground = Color(0xFFF1F3F8),
    surface = Color(0xFF11151D),
    onSurface = Color(0xFFF1F3F8),
    surfaceVariant = Color(0xFF1A202B),
    onSurfaceVariant = Color(0xFF98A2B3),
    error = Color(0xFFFF6361),
    outline = Color(0xFF39414F),
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
