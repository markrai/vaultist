package com.markrai.vaultist.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class HeadingColorPaletteTest {
    @Test
    fun fromIdDefaultsToClassic() {
        assertEquals(HeadingColorPalette.Classic, HeadingColorPalette.fromId(null))
        assertEquals(HeadingColorPalette.Classic, HeadingColorPalette.fromId("unknown"))
    }

    @Test
    fun fromIdResolvesKnownPalettes() {
        assertEquals(HeadingColorPalette.Classic, HeadingColorPalette.fromId("classic"))
        assertEquals(HeadingColorPalette.ClassicReversed, HeadingColorPalette.fromId("classic_reversed"))
        assertEquals(HeadingColorPalette.Teal, HeadingColorPalette.fromId("teal"))
        assertEquals(HeadingColorPalette.TealReversed, HeadingColorPalette.fromId("teal_reversed"))
    }

    @Test
    fun tealPaletteUsesTealForH4() {
        assertEquals(Color(0xFF26A69A), HeadingColorPalette.Teal.colorForLevel(4))
        assertEquals(Color(0xFF81C784), HeadingColorPalette.Classic.colorForLevel(4))
    }

    @Test
    fun reversedPalettesInvertHeadingLevels() {
        assertEquals(
            HeadingColorPalette.Classic.colorForLevel(6),
            HeadingColorPalette.ClassicReversed.colorForLevel(1),
        )
        assertEquals(
            HeadingColorPalette.Classic.colorForLevel(1),
            HeadingColorPalette.ClassicReversed.colorForLevel(6),
        )
        assertEquals(
            HeadingColorPalette.Teal.colorForLevel(6),
            HeadingColorPalette.TealReversed.colorForLevel(1),
        )
        assertEquals(
            HeadingColorPalette.Teal.colorForLevel(1),
            HeadingColorPalette.TealReversed.colorForLevel(6),
        )
    }
}
