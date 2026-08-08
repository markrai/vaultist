package com.markrai.vaultist.ui.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import com.markrai.vaultist.ui.theme.AppColorTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetThemeColorsTest {
    @Test
    fun mapsRubyLightPrimaryAndOnPrimary() {
        assertEquals(
            ColorProvider(Color(0xFFE0115F)),
            widgetPrimaryColor(AppColorTheme.Ruby, darkTheme = false),
        )
        assertEquals(
            ColorProvider(Color.White),
            widgetOnPrimaryColor(AppColorTheme.Ruby, darkTheme = false),
        )
    }

    @Test
    fun mapsForestDarkPrimaryAndOnPrimary() {
        assertEquals(
            ColorProvider(Color(0xFF4A8B5E)),
            widgetPrimaryColor(AppColorTheme.Forest, darkTheme = true),
        )
        assertEquals(
            ColorProvider(Color.White),
            widgetOnPrimaryColor(AppColorTheme.Forest, darkTheme = true),
        )
    }
}
