package com.masakasakasama.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.masakasakasama.designsystem.generated.TatsuAccent
import com.masakasakasama.designsystem.generated.TatsuColors
import com.masakasakasama.designsystem.generated.TatsuGeneratedColors
import com.masakasakasama.designsystem.generated.TatsuType

val LocalTatsuColors = staticCompositionLocalOf<TatsuColors> {
    error("TatsuTheme must wrap this composable")
}

private fun weight(value: Int): FontWeight = FontWeight(value)

private val TatsuTypography = Typography(
    displaySmall = TextStyle(
        fontSize = TatsuType.DisplaySize,
        lineHeight = TatsuType.DisplayLineHeight,
        fontWeight = weight(TatsuType.DisplayWeight),
    ),
    titleLarge = TextStyle(
        fontSize = TatsuType.TitleSize,
        lineHeight = TatsuType.TitleLineHeight,
        fontWeight = weight(TatsuType.TitleWeight),
    ),
    bodyLarge = TextStyle(
        fontSize = TatsuType.BodySize,
        lineHeight = TatsuType.BodyLineHeight,
        fontWeight = weight(TatsuType.BodyWeight),
    ),
    labelLarge = TextStyle(
        fontSize = TatsuType.LabelSize,
        lineHeight = TatsuType.LabelLineHeight,
        fontWeight = weight(TatsuType.LabelWeight),
    ),
)

@Composable
fun TatsuTheme(
    accent: TatsuAccent = TatsuAccent.Ocean,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = TatsuGeneratedColors.scheme(accent, darkTheme)
    val materialColors = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.accentContainer,
            background = colors.background,
            surface = colors.surface,
            surfaceVariant = colors.surfaceElevated,
            outline = colors.border,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            onSurfaceVariant = colors.textSecondary,
            error = colors.error,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.accentContainer,
            background = colors.background,
            surface = colors.surface,
            surfaceVariant = colors.surfaceElevated,
            outline = colors.border,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            onSurfaceVariant = colors.textSecondary,
            error = colors.error,
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalTatsuColors provides colors) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = TatsuTypography,
            content = content,
        )
    }
}
