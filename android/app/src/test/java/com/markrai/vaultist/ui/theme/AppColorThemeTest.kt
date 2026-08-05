package com.markrai.vaultist.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AppColorThemeTest {
    @Test
    fun fromIdReturnsRubyForNullOrUnknown() {
        assertEquals(AppColorTheme.Ruby, AppColorTheme.fromId(null))
        assertEquals(AppColorTheme.Ruby, AppColorTheme.fromId(""))
        assertEquals(AppColorTheme.Ruby, AppColorTheme.fromId("unknown"))
    }

    @Test
    fun fromIdParsesKnownThemes() {
        assertEquals(AppColorTheme.Ruby, AppColorTheme.fromId("ruby"))
        assertEquals(AppColorTheme.Forest, AppColorTheme.fromId("forest"))
    }
}
