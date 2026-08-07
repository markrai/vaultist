package com.markrai.vaultist.ui.markdown

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTypographyTest {
    @Test
    fun colorizedHeadingColorMapsLevelsOneThroughSix() {
        assertEquals(Color(0xFFE57373), colorizedHeadingColor(1))
        assertEquals(Color(0xFFFFB74D), colorizedHeadingColor(2))
        assertEquals(Color(0xFFFFD54F), colorizedHeadingColor(3))
        assertEquals(Color(0xFF81C784), colorizedHeadingColor(4))
        assertEquals(Color(0xFF64B5F6), colorizedHeadingColor(5))
        assertEquals(Color(0xFFBA68C8), colorizedHeadingColor(6))
    }

    @Test
    fun colorizedHeadingColorClampsOutOfRangeLevels() {
        assertEquals(colorizedHeadingColor(1), colorizedHeadingColor(0))
        assertEquals(colorizedHeadingColor(6), colorizedHeadingColor(99))
    }
}
