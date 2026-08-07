package com.markrai.vaultist.ui.widget

import androidx.glance.unit.ColorProvider
import com.markrai.vaultist.ui.markdown.colorizedHeadingColor

internal fun widgetHeadingTextColor(
    level: Int,
    colorizedHeadings: Boolean,
    defaultColor: ColorProvider,
): ColorProvider = if (colorizedHeadings) {
    ColorProvider(colorizedHeadingColor(level))
} else {
    defaultColor
}
