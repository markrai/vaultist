package com.markrai.vaultist.ui.components

import android.text.format.DateUtils
import com.markrai.vaultist.domain.ModifiedDateStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

fun parseModifiedAt(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return try {
        Instant.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }
}

fun formatModifiedAt(
    modifiedAt: String?,
    style: ModifiedDateStyle,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    nowMillis: Long = System.currentTimeMillis(),
): String? {
    val instant = parseModifiedAt(modifiedAt) ?: return null
    return when (style) {
        ModifiedDateStyle.Absolute -> {
            DateTimeFormatter.ofPattern("MMMM d yyyy", locale)
                .withZone(zoneId)
                .format(instant)
        }
        ModifiedDateStyle.Relative -> {
            DateUtils.getRelativeTimeSpanString(
                instant.toEpochMilli(),
                nowMillis,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            ).toString()
        }
    }
}
