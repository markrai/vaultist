package com.markrai.vaultist.ui.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WidgetHeadingColorsTest {
    @Test
    fun returnsDefaultColorWhenColorizedHeadingsDisabled() {
        val default = ColorProvider(Color.Red)

        val result = widgetHeadingTextColor(level = 1, colorizedHeadings = false, defaultColor = default)

        assertSame(default, result)
    }

    @Test
    fun mapsHeadingLevelsWhenColorizedHeadingsEnabled() {
        val default = ColorProvider(Color.Red)

        assertEquals(
            ColorProvider(Color(0xFFE57373)),
            widgetHeadingTextColor(level = 1, colorizedHeadings = true, defaultColor = default),
        )
        assertEquals(
            ColorProvider(Color(0xFFBA68C8)),
            widgetHeadingTextColor(level = 6, colorizedHeadings = true, defaultColor = default),
        )
    }
}
