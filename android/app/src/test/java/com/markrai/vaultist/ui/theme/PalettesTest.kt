package com.markrai.vaultist.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class PalettesTest {
    @Test
    fun rubyLightPaletteMatchesCurrentBrandColors() {
        val palette = paletteFor(AppColorTheme.Ruby, dark = false)
        assertEquals(Color(0xFFE0115F), palette.primary)
        assertEquals(Color(0xFFB80E4C), palette.primaryVariant)
        assertEquals(Color(0xFFDE3163), palette.browseMode)
    }

    @Test
    fun forestLightPaletteUsesGreenAccents() {
        val palette = paletteFor(AppColorTheme.Forest, dark = false)
        assertEquals(Color(0xFF2E5A3C), palette.primary)
        assertEquals(Color(0xFF1F3F2A), palette.primaryVariant)
        assertEquals(Color(0xFF3D7A52), palette.browseMode)
    }

    @Test
    fun lightPalettesShareSurfaces() {
        val ruby = paletteFor(AppColorTheme.Ruby, dark = false)
        val forest = paletteFor(AppColorTheme.Forest, dark = false)
        assertEquals(ruby.background, forest.background)
        assertEquals(ruby.onBackground, forest.onBackground)
    }

    @Test
    fun rubyDarkPaletteUsesLightenedAccentsOnDarkSurfaces() {
        val palette = paletteFor(AppColorTheme.Ruby, dark = true)
        assertEquals(Color(0xFFFF6B8F), palette.primary)
        assertEquals(Color(0xFFFF8FA8), palette.primaryVariant)
        assertEquals(Color(0xFFDE3163), palette.browseMode)
        assertEquals(Color(0xFF1C1B1F), palette.background)
        assertEquals(Color(0xFFE6E1E5), palette.onBackground)
    }

    @Test
    fun forestDarkPaletteUsesLightenedGreenAccents() {
        val palette = paletteFor(AppColorTheme.Forest, dark = true)
        assertEquals(Color(0xFF4A8B5E), palette.primary)
        assertEquals(Color(0xFF3D7A52), palette.primaryVariant)
        assertEquals(Color(0xFF4A8B5E), palette.browseMode)
        assertEquals(Color(0xFF1C1B1F), palette.background)
    }

    @Test
    fun darkPalettesShareSurfaces() {
        val ruby = paletteFor(AppColorTheme.Ruby, dark = true)
        val forest = paletteFor(AppColorTheme.Forest, dark = true)
        assertEquals(ruby.background, forest.background)
        assertEquals(ruby.onBackground, forest.onBackground)
    }
}
