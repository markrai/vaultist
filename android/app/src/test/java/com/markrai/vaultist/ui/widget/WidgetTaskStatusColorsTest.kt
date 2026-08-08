package com.markrai.vaultist.ui.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WidgetTaskStatusColorsTest {
    @Test
    fun returnsDefaultColorWhenColorizeDisabled() {
        val default = ColorProvider(Color.Red)

        val result = widgetTaskStatusTextColor(
            checked = false,
            colorizeCheckboxStatus = false,
            defaultColor = default,
        )

        assertSame(default, result)
    }

    @Test
    fun returnsDefaultColorForNonTaskListItem() {
        val default = ColorProvider(Color.Red)

        val result = widgetTaskStatusTextColor(
            checked = null,
            colorizeCheckboxStatus = true,
            defaultColor = default,
        )

        assertSame(default, result)
    }

    @Test
    fun mapsUncheckedTaskToRedWhenEnabled() {
        val default = ColorProvider(Color.Red)

        assertEquals(
            ColorProvider(Color(0xFFC62828)),
            widgetTaskStatusTextColor(
                checked = false,
                colorizeCheckboxStatus = true,
                defaultColor = default,
            ),
        )
    }

    @Test
    fun mapsCheckedTaskToGreenWhenEnabled() {
        val default = ColorProvider(Color.Red)

        assertEquals(
            ColorProvider(Color(0xFF2E7D32)),
            widgetTaskStatusTextColor(
                checked = true,
                colorizeCheckboxStatus = true,
                defaultColor = default,
            ),
        )
    }
}
