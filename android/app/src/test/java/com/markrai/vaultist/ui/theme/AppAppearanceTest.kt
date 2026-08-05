package com.markrai.vaultist.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AppAppearanceTest {
    @Test
    fun fromIdReturnsLightForNullOrUnknown() {
        assertEquals(AppAppearance.Light, AppAppearance.fromId(null))
        assertEquals(AppAppearance.Light, AppAppearance.fromId(""))
        assertEquals(AppAppearance.Light, AppAppearance.fromId("unknown"))
    }

    @Test
    fun fromIdParsesKnownAppearances() {
        assertEquals(AppAppearance.Light, AppAppearance.fromId("light"))
        assertEquals(AppAppearance.Dark, AppAppearance.fromId("dark"))
    }
}
