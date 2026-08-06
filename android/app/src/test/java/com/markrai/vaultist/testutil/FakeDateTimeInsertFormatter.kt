package com.markrai.vaultist.testutil

import com.markrai.vaultist.data.settings.DateTimeInsertFormatter

class FakeDateTimeInsertFormatter(
    private val formatted: String = "2026-01-01 12:00",
) : DateTimeInsertFormatter {
    override fun formatNow(): String = formatted
}
