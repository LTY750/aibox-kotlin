package com.aibox.kotlin.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.times
import com.aibox.kotlin.core.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF2A6EF4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF001B44),
    secondary = Color(0xFF565E71),
    secondaryContainer = Color(0xFFDBE2F9),
    surface = Color(0xFFFAFAFD),
    surfaceVariant = Color(0xFFE1E2EC),
    background = Color(0xFFFAFAFD),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF0A305F),
    primaryContainer = Color(0xFF274777),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFFBEC6DC),
    secondaryContainer = Color(0xFF3E4759),
    surface = Color(0xFF121318),
    surfaceVariant = Color(0xFF44464F),
    background = Color(0xFF121318),
)

/** 字体大小百分比（100=默认）换算为排版缩放；越界钳制到 [80,160]。 */
private const val FONT_SCALE_MIN = 80
private const val FONT_SCALE_MAX = 160
private const val FONT_SCALE_DEFAULT = 100

private val BaseTypography = Typography()

/**
 * 依据设置里的字体大小百分比缩放默认 Material3 排版。
 * 只缩放已明确尺寸的 sp 值，`Unspecified` 值原样保留，避免运行时异常。
 */
private fun scaledTypography(fontSizePercent: Int?): Typography {
    val percent = (fontSizePercent ?: FONT_SCALE_DEFAULT).coerceIn(FONT_SCALE_MIN, FONT_SCALE_MAX)
    val scale = percent / 100f
    if (scale == 1f) return BaseTypography

    fun TextStyle.scaled(): TextStyle = copy(
        fontSize = if (fontSize != TextUnit.Unspecified) fontSize * scale else fontSize,
        lineHeight = if (lineHeight != TextUnit.Unspecified) lineHeight * scale else lineHeight,
    )

    val base = BaseTypography
    return Typography(
        displayLarge = base.displayLarge.scaled(),
        displayMedium = base.displayMedium.scaled(),
        displaySmall = base.displaySmall.scaled(),
        headlineLarge = base.headlineLarge.scaled(),
        headlineMedium = base.headlineMedium.scaled(),
        headlineSmall = base.headlineSmall.scaled(),
        titleLarge = base.titleLarge.scaled(),
        titleMedium = base.titleMedium.scaled(),
        titleSmall = base.titleSmall.scaled(),
        bodyLarge = base.bodyLarge.scaled(),
        bodyMedium = base.bodyMedium.scaled(),
        bodySmall = base.bodySmall.scaled(),
        labelLarge = base.labelLarge.scaled(),
        labelMedium = base.labelMedium.scaled(),
        labelSmall = base.labelSmall.scaled(),
    )
}

/**
 * 应用主题。
 *
 * @param themeMode 主题数值枚举：0=Dark 1=Light 2=System（null 视为跟随系统）。
 * @param fontSizePercent 字体大小百分比（null 视为 100）。
 */
@Composable
fun AiboxTheme(
    themeMode: Int? = null,
    fontSizePercent: Int? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = scaledTypography(fontSizePercent),
        content = content,
    )
}
