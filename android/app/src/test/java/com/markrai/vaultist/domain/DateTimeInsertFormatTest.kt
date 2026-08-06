package com.markrai.vaultist.domain

import java.time.LocalDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DateTimeInsertFormatTest {
    private val sample = LocalDateTime.of(2026, 8, 6, 9, 30, 45)

    @Test
    fun isoDateTimeFormatsCompactTimestamp() {
        assertEquals("2026-08-06 09:30", DateTimeInsertFormat.IsoDateTime.format(sample))
    }

    @Test
    fun isoDateFormatsDateOnly() {
        assertEquals("2026-08-06", DateTimeInsertFormat.IsoDate.format(sample))
    }

    @Test
    fun isoDateTimeSecondsIncludesSeconds() {
        assertEquals("2026-08-06 09:30:45", DateTimeInsertFormat.IsoDateTimeSeconds.format(sample))
    }

    @Test
    fun localizedDateTimeUsesLocale() {
        assertEquals(
            "Aug 6, 2026, 9:30 AM",
            DateTimeInsertFormat.LocalizedDateTime.format(sample, Locale.US),
        )
    }

    @Test
    fun previewSampleUsesFixedExample() {
        assertEquals("2026-08-06 09:30", DateTimeInsertFormat.IsoDateTime.previewSample())
    }

    @Test
    fun fromIdDefaultsToIsoDateTime() {
        assertEquals(DateTimeInsertFormat.IsoDateTime, DateTimeInsertFormat.fromId(null))
        assertEquals(DateTimeInsertFormat.IsoDateTime, DateTimeInsertFormat.fromId("unknown"))
        assertEquals(DateTimeInsertFormat.IsoDate, DateTimeInsertFormat.fromId("iso_date"))
    }
}
