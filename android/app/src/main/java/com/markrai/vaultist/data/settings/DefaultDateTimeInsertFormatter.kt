package com.markrai.vaultist.data.settings

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultDateTimeInsertFormatter @Inject constructor() : DateTimeInsertFormatter {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    override fun formatNow(): String = LocalDateTime.now().format(formatter)
}
