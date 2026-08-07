package com.markrai.vaultist.ui.markdown

import androidx.compose.ui.graphics.Color
import com.markrai.vaultist.ui.theme.HeadingColorPalette
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTypographyTest {
    @Test
    fun classicPaletteMapsLevelsOneThroughSix() {
        assertEquals(Color(0xFFE57373), colorizedHeadingColor(1, HeadingColorPalette.Classic))
        assertEquals(Color(0xFFFFB74D), colorizedHeadingColor(2, HeadingColorPalette.Classic))
        assertEquals(Color(0xFFFFD54F), colorizedHeadingColor(3, HeadingColorPalette.Classic))
        assertEquals(Color(0xFF81C784), colorizedHeadingColor(4, HeadingColorPalette.Classic))
        assertEquals(Color(0xFF64B5F6), colorizedHeadingColor(5, HeadingColorPalette.Classic))
        assertEquals(Color(0xFFBA68C8), colorizedHeadingColor(6, HeadingColorPalette.Classic))
    }

    @Test
    fun tealPaletteMapsLevelsOneThroughSix() {
        assertEquals(Color(0xFFE53935), colorizedHeadingColor(1, HeadingColorPalette.Teal))
        assertEquals(Color(0xFFFB8C00), colorizedHeadingColor(2, HeadingColorPalette.Teal))
        assertEquals(Color(0xFFFFC107), colorizedHeadingColor(3, HeadingColorPalette.Teal))
        assertEquals(Color(0xFF26A69A), colorizedHeadingColor(4, HeadingColorPalette.Teal))
        assertEquals(Color(0xFF1E88E5), colorizedHeadingColor(5, HeadingColorPalette.Teal))
        assertEquals(Color(0xFF8E24AA), colorizedHeadingColor(6, HeadingColorPalette.Teal))
    }

    @Test
    fun colorizedHeadingColorClampsOutOfRangeLevels() {
        assertEquals(
            colorizedHeadingColor(1, HeadingColorPalette.Classic),
            colorizedHeadingColor(0, HeadingColorPalette.Classic),
        )
        assertEquals(
            colorizedHeadingColor(6, HeadingColorPalette.Teal),
            colorizedHeadingColor(99, HeadingColorPalette.Teal),
        )
    }

    @Test
    fun reversedClassicMapsH1ToClassicH6() {
        assertEquals(
            colorizedHeadingColor(6, HeadingColorPalette.Classic),
            colorizedHeadingColor(1, HeadingColorPalette.ClassicReversed),
        )
    }
}
