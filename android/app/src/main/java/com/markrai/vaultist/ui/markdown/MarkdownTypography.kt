package com.markrai.vaultist.ui.markdown

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.markrai.vaultist.R
import com.markrai.vaultist.ui.theme.HeadingColorPalette

val LocalColorizedHeadings = staticCompositionLocalOf { false }
val LocalColorizeCheckboxStatus = staticCompositionLocalOf { false }
val LocalHeadingColorPalette = staticCompositionLocalOf { HeadingColorPalette.Classic }

private val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.inter_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.inter_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.inter_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

internal fun colorizedHeadingColor(
    level: Int,
    palette: HeadingColorPalette = HeadingColorPalette.Classic,
): Color = palette.colorForLevel(level)

/** Read-only markdown prose typography (Inter + standard ligatures). Not used for edit or code. */
object MarkdownTypography {
    const val ProseFeatures = "liga, clig"

    @Composable
    fun body(): TextStyle = MaterialTheme.typography.body1.copy(
        fontFamily = InterFontFamily,
        fontFeatureSettings = ProseFeatures,
    )

    @Composable
    fun quote(): TextStyle = body().copy(fontStyle = FontStyle.Italic)

    @Composable
    fun heading(
        level: Int,
        colorized: Boolean = LocalColorizedHeadings.current,
        palette: HeadingColorPalette = LocalHeadingColorPalette.current,
    ): TextStyle {
        val base = TextStyle(
            fontFamily = InterFontFamily,
            fontFeatureSettings = ProseFeatures,
            fontSize = when (level) {
                1 -> 30.sp
                2 -> 24.sp
                3 -> 21.sp
                else -> 18.sp
            },
            fontWeight = FontWeight.Bold,
        )
        return if (colorized) base.copy(color = colorizedHeadingColor(level, palette)) else base
    }
}
