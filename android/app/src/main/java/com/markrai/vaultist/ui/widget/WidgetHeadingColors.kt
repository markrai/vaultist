package com.markrai.vaultist.ui.widget

import androidx.glance.unit.ColorProvider
import com.markrai.vaultist.ui.markdown.colorizedHeadingColor
import com.markrai.vaultist.ui.theme.HeadingColorPalette

internal fun widgetHeadingTextColor(
    level: Int,
    colorizedHeadings: Boolean,
    defaultColor: ColorProvider,
    palette: HeadingColorPalette = HeadingColorPalette.Classic,
): ColorProvider = if (colorizedHeadings) {
    ColorProvider(colorizedHeadingColor(level, palette))
} else {
    defaultColor
}
