// GENERATED FILE. Edit tokens/design-tokens.json, then run npm run generate.
package com.masakasakasama.designsystem.generated

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class TatsuAccent { Ocean, Sage, Amethyst }

data class TatsuColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentHover: Color,
    val accentContainer: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color
)

object TatsuGeneratedColors {
    val OceanLight = TatsuColors(
        background = Color(0xFFF5F7FA),
        surface = Color(0xFFFFFFFF),
        surfaceElevated = Color(0xFFEEF1F4),
        border = Color(0xFFD7DCE2),
        textPrimary = Color(0xFF111316),
        textSecondary = Color(0xFF48515E),
        textMuted = Color(0xFF66717F),
        accent = Color(0xFF006EDC),
        accentHover = Color(0xFF0056B3),
        accentContainer = Color(0xFFDDF2FF),
        onAccent = Color(0xFFFFFFFF),
        success = Color(0xFF33D17A),
        warning = Color(0xFFF5B942),
        error = Color(0xFFFF5E66),
        info = Color(0xFF5AA9FF)
    )

    val OceanDark = TatsuColors(
        background = Color(0xFF070809),
        surface = Color(0xFF111316),
        surfaceElevated = Color(0xFF181B20),
        border = Color(0xFF242831),
        textPrimary = Color(0xFFF5F7FA),
        textSecondary = Color(0xFFADB5C1),
        textMuted = Color(0xFF7E8795),
        accent = Color(0xFF3AAEFF),
        accentHover = Color(0xFF6EC6FF),
        accentContainer = Color(0xFF0056B3),
        onAccent = Color(0xFF000000),
        success = Color(0xFF33D17A),
        warning = Color(0xFFF5B942),
        error = Color(0xFFFF5E66),
        info = Color(0xFF5AA9FF)
    )

    val SageLight = TatsuColors(
        background = Color(0xFFF5F7FA),
        surface = Color(0xFFFFFFFF),
        surfaceElevated = Color(0xFFEEF1F4),
        border = Color(0xFFD7DCE2),
        textPrimary = Color(0xFF111316),
        textSecondary = Color(0xFF48515E),
        textMuted = Color(0xFF66717F),
        accent = Color(0xFF2A9C68),
        accentHover = Color(0xFF207A52),
        accentContainer = Color(0xFFDDF7E9),
        onAccent = Color(0xFFFFFFFF),
        success = Color(0xFF33D17A),
        warning = Color(0xFFF5B942),
        error = Color(0xFFFF5E66),
        info = Color(0xFF5AA9FF)
    )

    val SageDark = TatsuColors(
        background = Color(0xFF070809),
        surface = Color(0xFF111316),
        surfaceElevated = Color(0xFF181B20),
        border = Color(0xFF242831),
        textPrimary = Color(0xFFF5F7FA),
        textSecondary = Color(0xFFADB5C1),
        textMuted = Color(0xFF7E8795),
        accent = Color(0xFF62C998),
        accentHover = Color(0xFF8EDDB8),
        accentContainer = Color(0xFF207A52),
        onAccent = Color(0xFF000000),
        success = Color(0xFF33D17A),
        warning = Color(0xFFF5B942),
        error = Color(0xFFFF5E66),
        info = Color(0xFF5AA9FF)
    )

    val AmethystLight = TatsuColors(
        background = Color(0xFFF5F7FA),
        surface = Color(0xFFFFFFFF),
        surfaceElevated = Color(0xFFEEF1F4),
        border = Color(0xFFD7DCE2),
        textPrimary = Color(0xFF111316),
        textSecondary = Color(0xFF48515E),
        textMuted = Color(0xFF66717F),
        accent = Color(0xFF7547DB),
        accentHover = Color(0xFF5E35B1),
        accentContainer = Color(0xFFF0E8FF),
        onAccent = Color(0xFFFFFFFF),
        success = Color(0xFF33D17A),
        warning = Color(0xFFF5B942),
        error = Color(0xFFFF5E66),
        info = Color(0xFF5AA9FF)
    )

    val AmethystDark = TatsuColors(
        background = Color(0xFF070809),
        surface = Color(0xFF111316),
        surfaceElevated = Color(0xFF181B20),
        border = Color(0xFF242831),
        textPrimary = Color(0xFFF5F7FA),
        textSecondary = Color(0xFFADB5C1),
        textMuted = Color(0xFF7E8795),
        accent = Color(0xFFAB82FF),
        accentHover = Color(0xFFC6A8FF),
        accentContainer = Color(0xFF5E35B1),
        onAccent = Color(0xFF000000),
        success = Color(0xFF33D17A),
        warning = Color(0xFFF5B942),
        error = Color(0xFFFF5E66),
        info = Color(0xFF5AA9FF)
    )

    fun scheme(accent: TatsuAccent, dark: Boolean): TatsuColors = when (accent) {
        TatsuAccent.Ocean -> if (dark) OceanDark else OceanLight
        TatsuAccent.Sage -> if (dark) SageDark else SageLight
        TatsuAccent.Amethyst -> if (dark) AmethystDark else AmethystLight
    }
}

object TatsuDimens {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp
    val Xxxl = 48.dp
    val RadiusSm = 8.dp
    val RadiusMd = 12.dp
    val RadiusLg = 16.dp
    val RadiusXl = 24.dp
    val CardRadius = 16.dp
    val CardPadding = 16.dp
    val CardGap = 12.dp
    val CardBorderWidth = 1.dp
    val ButtonRadius = 12.dp
    val ButtonHeight = 48.dp
}

object TatsuType {
    val DisplaySize = 34.sp
    val DisplayLineHeight = 40.sp
    const val DisplayWeight = 700
    val TitleSize = 22.sp
    val TitleLineHeight = 28.sp
    const val TitleWeight = 700
    val BodySize = 16.sp
    val BodyLineHeight = 24.sp
    const val BodyWeight = 400
    val LabelSize = 13.sp
    val LabelLineHeight = 18.sp
    const val LabelWeight = 600
    val MetricSize = 28.sp
    val MetricLineHeight = 34.sp
    const val MetricWeight = 700
}
