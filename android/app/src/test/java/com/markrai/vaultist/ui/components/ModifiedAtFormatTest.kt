package com.markrai.vaultist.ui.components

import com.markrai.vaultist.domain.ModifiedDateStyle
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModifiedAtFormatTest {
    @Test
    fun absoluteFormatsAsMonthDayYear() {
        val formatted = formatModifiedAt(
            modifiedAt = "2026-07-26T12:00:00Z",
            style = ModifiedDateStyle.Absolute,
            zoneId = ZoneId.of("UTC"),
            locale = Locale.US,
        )
        assertEquals("July 26 2026", formatted)
    }

    @Test
    fun nullModifiedAtReturnsNull() {
        assertNull(formatModifiedAt(null, ModifiedDateStyle.Absolute))
    }

    @Test
    fun invalidModifiedAtReturnsNull() {
        assertNull(formatModifiedAt("not-a-date", ModifiedDateStyle.Absolute))
    }

    @Test
    fun parseModifiedAtParsesIsoInstant() {
        assertEquals(Instant.parse("2026-01-02T00:00:00Z"), parseModifiedAt("2026-01-02T00:00:00Z"))
    }

    @Test
    fun parseModifiedAtReturnsNullForInvalid() {
        assertNull(parseModifiedAt("not-a-date"))
    }
}
