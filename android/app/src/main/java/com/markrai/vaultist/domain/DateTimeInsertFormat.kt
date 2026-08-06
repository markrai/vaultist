package com.markrai.vaultist.domain

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

enum class DateTimeInsertFormat(
    val id: String,
    val label: String,
) {
    IsoDateTime("iso_date_time", "Date and time"),
    IsoDate("iso_date", "Date only"),
    IsoDateTimeSeconds("iso_date_time_seconds", "Date and time (seconds)"),
    LocalizedDateTime("localized", "Local format"),
    ;

    fun format(at: LocalDateTime = LocalDateTime.now(), locale: Locale = Locale.getDefault()): String =
        when (this) {
            IsoDateTime -> ISO_DATE_TIME.format(at)
            IsoDate -> ISO_DATE.format(at)
            IsoDateTimeSeconds -> ISO_DATE_TIME_SECONDS.format(at)
            LocalizedDateTime -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
                .format(at)
        }

    fun previewSample(): String = format(PREVIEW_SAMPLE, Locale.US)

    companion object {
        private val PREVIEW_SAMPLE = LocalDateTime.of(2026, 8, 6, 9, 30)
        private val ISO_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        private val ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val ISO_DATE_TIME_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        fun fromId(id: String?): DateTimeInsertFormat =
            entries.firstOrNull { it.id == id } ?: IsoDateTime
    }
}
