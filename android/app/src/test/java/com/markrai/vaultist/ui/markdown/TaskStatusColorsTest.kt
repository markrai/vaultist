package com.markrai.vaultist.ui.markdown

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskStatusColorsTest {
    @Test
    fun uncheckedUsesRedInLightTheme() {
        assertEquals(Color(0xFFC62828), taskStatusComposeColor(checked = false))
    }

    @Test
    fun checkedUsesGreenInLightTheme() {
        assertEquals(Color(0xFF2E7D32), taskStatusComposeColor(checked = true))
    }
}
